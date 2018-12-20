package org.kurento_android.ui;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import org.kurento_android.R;

/**
 * Created by cxm on 2/28/17.
 */
public class CallActivity extends BaseActivity {
    //public static final String WS_ADDR = "wss://120.78.210.210:8443/";
    public static final String TURN_ADDRESS = "turn:120.78.176.218:3478?transport=udp";
    public static final String TURN_USERNAME = "ubonass";
    public static final String TURN_PASSWORD = "openvidu";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_call2);

        SelectFragment selectFragment = new SelectFragment();
        getSupportFragmentManager().beginTransaction().replace(
                R.id.call_frame, selectFragment).commit();
    }
}
