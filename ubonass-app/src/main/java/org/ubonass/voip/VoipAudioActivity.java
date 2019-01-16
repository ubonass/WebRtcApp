package org.ubonass.voip;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import org.ubonass.BaseActivity;
import org.ubonass.R;

public class VoipAudioActivity extends BaseActivity implements View.OnClickListener {

    private Chronometer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN ,
                WindowManager.LayoutParams. FLAG_FULLSCREEN);
        setContentView(R.layout.activity_voip_audio);
        timer = (Chronometer) findViewById(R.id.timer);

    }


    @Override
    public void onResume(){
        super.onResume();
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    @Override
    public void onRestart(){
        super.onRestart();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onBackPressed(){

    }


    private void showCallingView(){
        findViewById(R.id.calling_txt).setVisibility(View.VISIBLE);
        findViewById(R.id.timer).setVisibility(View.INVISIBLE);
    }

    private void showTalkingView(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.calling_txt).setVisibility(View.INVISIBLE);
                findViewById(R.id.timer).setVisibility(View.VISIBLE);
                timer.setBase(SystemClock.elapsedRealtime());
                timer.start();
            }
        });
    }


    @Override
    public void onClick(View v) {

    }

}
