package com.cyj.com.im;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @InjectView(R.id.bt_file)
    Button btFile;
    @InjectView(R.id.bt_stream)
    Button btStream;
    private Button file, stream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
    }

    @OnClick({R.id.bt_file, R.id.bt_stream})
    public void onClick(View view) {
        Intent intent=new Intent();
        switch (view.getId()) {
            case R.id.bt_file:
                intent.setClass(MainActivity.this,FileActivity.class);
                break;
            case R.id.bt_stream:
                intent.setClass(MainActivity.this,StreamActivity.class);
                break;
        }
        startActivity(intent);
        }
}
