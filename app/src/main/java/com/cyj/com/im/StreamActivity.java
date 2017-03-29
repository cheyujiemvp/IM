package com.cyj.com.im;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

import static java.lang.System.in;

public class StreamActivity extends AppCompatActivity {

    @InjectView(R.id.bt_start)
    Button btStart;
    @InjectView(R.id.tv_log)
    TextView tvLog;
    @InjectView(R.id.bt_play)
    Button btPlay;

    //录音状态，volatile 保证多线程内存同步，避免出问题
    private volatile boolean mIsRecording;
    private ExecutorService service;
    private Handler mMainThreadHandler;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;
    //buffer 不能太大，避免oom
    private static final int BUFFER_SIZE = 2048;
    private byte[] mBuffer;
    private FileOutputStream fops;
    private AudioRecord mAudioRecord;
    private volatile boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        ButterKnife.inject(this);

        mBuffer = new byte[BUFFER_SIZE];
        service = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    @OnClick(R.id.bt_start)
    public void onClick() {
        //根据当前状态，改变UI，执行开始/停止录音的逻辑
        if (mIsRecording) {
            //改变UI状态
            btStart.setText("开始");
            //改变录音状态
            mIsRecording = false;
        } else {//改变UI状态
            btStart.setText("停止");
            //改变录音状态
            mIsRecording = true;
            //提交后台任务，执行录音
            service.submit(new Runnable() {
                @Override
                public void run() {
                    //执行开始录音逻辑，失败提示用户
                    if (!startRecord()) {
                        recordFail();
                    }
                }
            });
        }
    }

    /***
     * 录音错误处理
     */
    private void recordFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
                //重置录音状态以及UI
                mIsRecording = false;
                btStart.setText("开始");
            }
        });
    }

    /**
     * 启动录音逻辑
     *
     * @return
     */
    private boolean startRecord() {

        try {
            //创建录音文件
            mAudioFile = new File(
                    Environment.getExternalStorageDirectory() + "/IM/" + System.currentTimeMillis()
                            + ".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //创建文件输出流
            fops = new FileOutputStream(mAudioFile);
            //配置AudioRecord
            //从麦克风采集
            int audioSource = MediaRecorder.AudioSource.MIC;
            int sampleRate = 44100;//所有安卓系统都支持的频率
            //单声道输入
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            //pcm 16是所有安卓系统都支持
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            //计算AudioRecord内部buffer最小的大小
            int minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate, channelConfig, audioFormat);
            //buffer 不能小于最低要求，也不能小于我们每次读取的大小
            mAudioRecord = new AudioRecord(
                    audioSource, sampleRate, channelConfig,
                    audioFormat, Math.max(minBufferSize, BUFFER_SIZE));

            //开始录音
            mAudioRecord.startRecording();
            //记录开始录音时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();
            //循环读取数据，写到输出流中
            while (mIsRecording) {
                //只要还在录音状态，就一直读取数据
                int read = mAudioRecord.read(mBuffer, 0, BUFFER_SIZE);
                if (read > 0) {
                    //读取成功就写在文件中
                    fops.write(mBuffer, 0, read);
                } else {
                    //读取失败，返回false 提示用户
                    return false;
                }
            }

            //退出循环，停止录音，释放资源
            return stopRecord();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            {
                //释放AudioRecord
                if (mAudioRecord != null) {
                    mAudioRecord.release();
                }
            }
        }
    }

    /**
     * 结束录音逻辑
     *
     * @return
     */
    private boolean stopRecord() {

        try {
            //停止录音，关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            fops.close();
            //记录结束时间，统计录音时长
            mStopRecordTime = System.currentTimeMillis();

            //大于3秒才算成功，在主线程改变UI显示
            final int second = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            if (second > 3) {
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvLog.setText(tvLog.getText() + "\n录音成功" + second + "秒");
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity 销毁时，释放资源，避免内存泄漏
        service.shutdownNow();
    }

    @OnClick(R.id.bt_play)
    public void play() {

        //检查播放状态，防止重复播放
        if (mAudioFile!=null&&!mIsPlaying)
        {
            //设置当前播放状态
            mIsPlaying=true;
            //在后台线程提交播放任务，防止阻塞主线程
            service.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
        }
    }

    /**
     * 实际播放逻辑
     * @param mAudioFile
     */

    private void doPlay(File mAudioFile) {
        //配置播放器
        //音乐类型，扬声器播放
        int stremType= AudioManager.STREAM_MUSIC;
        //录音时用的采样频率，所以播放时候使用同样的采样频率
        int sampleRate=44100;
        //MONO表示单声道，录音用输入单声道，播放用输出单声道
        int channelConfig=AudioFormat.CHANNEL_OUT_MONO;
        //录音时使用16bit 所以播放时使用同样的格式
        int audioFormat=AudioFormat.ENCODING_PCM_16BIT;
        //流模式
        int mode= AudioTrack.MODE_STREAM;

        //计算最小buffer的大小
        int minBufferSize=AudioTrack.getMinBufferSize(sampleRate,
                channelConfig,audioFormat);
        //构造AudioTrack
        AudioTrack mAudioTrack=new AudioTrack(stremType,sampleRate,
                channelConfig,
                audioFormat,
                //不能小于AudioTrack的最低要求，也不能小于我们每次读的大小
                Math.max(minBufferSize,BUFFER_SIZE),
                mode);


        try {
            //从文件流中读数据
            FileInputStream in=null;
            in=new FileInputStream(mAudioFile);
            int read=0;
            Log.d("xxxxxxx","fdasas");
            //只要没读完，循环写播放
            while ((read=in.read(mBuffer))>0)
            {
                int ret=mAudioTrack.write(mBuffer,0,read);
                switch (ret) {
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioManager.ERROR_DEAD_OBJECT:
                        playFail();
                        return;
                    default:
                        break;
                }
            }
            //循环读数据，写到播放器去播放
        } catch (Exception e) {
            e.printStackTrace();
            //错误处理，防止闪退
            playFail();
        }
        finally {
            mIsPlaying=false;
            //关闭文件输入流
            if (in!=null)
            {
                closequick(in);
            }
            //播放器释放
            resetquick(mAudioTrack);

        }

    }

    private void resetquick(AudioTrack mAudioTrack) {
        try {
            mAudioTrack.stop();
            mAudioTrack.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 静默关闭输入流
     * @param in
     */
    private void closequick(InputStream in) {
        try {
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 播放错误处理
     */
    private void playFail() {
        mAudioFile=null;
        //给用户toast提示失败，要在主线程执行
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
