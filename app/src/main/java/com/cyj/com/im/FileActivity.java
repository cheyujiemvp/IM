package com.cyj.com.im;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class FileActivity extends AppCompatActivity implements View.OnTouchListener {

    @InjectView(R.id.tv_msg)
    TextView tvMsg;
    @InjectView(R.id.tv_say)
    TextView tvSay;
    @InjectView(R.id.activity_file)
    FrameLayout activityFile;
    @InjectView(R.id.bt_play)
    Button btPlay;

    private ExecutorService service;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;
    private Handler mMainThreadHandler;
    //主线程和后台播放数据同步
    private volatile boolean mIsplaying;
    private MediaPlayer mMediaPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        ButterKnife.inject(this);
        //按下说话 释放发送
        tvSay.setOnTouchListener(this);
        //录音JNI函数不具备线程安全性，要用单线程
        service = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startRecord();
                break;
            case MotionEvent.ACTION_UP:
                stopRecord();
                break;
            case MotionEvent.ACTION_CANCEL:
                break;
        }

        //处理点击事件返回true
        return true;
    }

    //开始录音
    private void startRecord() {
        tvSay.setText("正在说话");
        tvSay.setBackgroundResource(R.drawable.shape_speaking);

        //开启进行录音
        service.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前的录音
                releaseRecord();
                //提示录音失败
                if (!doStart()) {
                    recordFail();
                }
            }


        });
    }

    //录音错误
    private void recordFail() {
        mAudioFile = null;
        //给用户toast提示失败,要在主线程进行
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    //启动录音
    private boolean doStart() {
        try {
            //1创建MediaRecorder
            mMediaRecorder = new MediaRecorder();
            //2创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/IM/" + System.currentTimeMillis() + ".m4a");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //3.配置MediaRecorder

            //从麦克风采集
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //保存文件为MP4格式
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //所有安卓系统都支持的采样频率
            mMediaRecorder.setAudioSamplingRate(44100);
            //通用的AAC 编码格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            //音质比较好的频率
            mMediaRecorder.setAudioEncodingBitRate(96000);
            //设置录音文件的位置
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());


            //4开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            //记录开始录音的时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        //录音成功
        return true;
    }

    //停止录音
    private boolean doStop() {
        try {
            //停止录音
            mMediaRecorder.stop();
            //记录停止时间，统计时长
            mStopRecordTime = System.currentTimeMillis();
            //只接受超过3秒的录音，在UI上显示
            final int second = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            if (second > 3) {
                //在主线程改UI,显示出来
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvMsg.setText(tvMsg.getText() + "\n录音成功" + second + "秒");
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    //释放录音
    private void releaseRecord() {
        //检查MediaRecorder不为空
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    //停止录音
    private void stopRecord() {
        tvSay.setText("按住说话");
        tvSay.setBackgroundResource(R.drawable.shape_nomal);
        //停止进行录音
        service.submit(new Runnable() {
            @Override
            public void run() {
                //执行停止录音，失败提醒用户
                if (!doStop()) {
                    recordFail();
                }
                //释放MediaRecorder
                releaseRecord();
            }
        });
    }


    //activity销毁时停止后台任务，防止内存泄漏
    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.shutdown();
        releaseRecord();
        stopPlay();
    }



    @OnClick(R.id.bt_play)
    public void onClick() {
        //检查当前状态，防止重复播放
        if (mAudioFile!=null&&!mIsplaying)
        {
            //设置当前播放状态
            mIsplaying=true;
            service.submit(new Runnable() {
                @Override
                 public void run() {
                   doPlay(mAudioFile);
                }
            });
        }
    }

    /***
     * 实际播放逻辑
     * @param mAudioFile
     */
    private void doPlay(File mAudioFile) {
        //配置播放器 MediaPlayer
        mMediaPlayer=new MediaPlayer();
        try {
            //设置声音文件
            mMediaPlayer.setDataSource(mAudioFile.getAbsolutePath());
            //设置监听回调
          mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
              @Override
              public void onCompletion(MediaPlayer mp) {
                  //播放结束
                  stopPlay();
              }
          });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    //提醒用户
                    playFail();

                    //释放播放器
                    stopPlay();
                    //错误处理，返回true
                    return true;
                }
            });
            //配置音量，是否循环
            mMediaPlayer.setVolume(1,1);
            mMediaPlayer.setLooping(false);
            //准备，开始
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            //异常处理，防止闪退
        } catch (Exception e) {
            e.printStackTrace();
            //提醒用户
            playFail();

            //释放播放器
            stopPlay();
        }
    }

    /**
     * 提醒用户播放失败
     */
    private void playFail() {
        //在主线程toast提示
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 停止播放的逻辑
     */
    private void stopPlay() {
        //重置播放状态
        mIsplaying=false;
        //释放播放器
        if (mMediaPlayer!=null)
        {
            //重置监听器，防止内存泄漏
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);

            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer=null;
        }
    }
}
