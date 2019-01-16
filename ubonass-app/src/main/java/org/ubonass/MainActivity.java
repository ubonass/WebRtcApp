package org.ubonass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.TextView;
import org.ubonass.voip.VoipListActivity;

/**
 * 初始化各种参数
 */
public class MainActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get setting keys.
        setContentView(R.layout.activity_main);
        ((ImageView) findViewById(R.id.user_info_logo))
                .setImageResource(AppManager.getLogoImage(this,
                        AppManager.userId));
        ((TextView) findViewById(R.id.user_info_id))
                .setText(AppManager.loadSharedData(getApplicationContext(),
                        "userId"));
        findViewById(R.id.btn_main_im).setOnClickListener(this);
        findViewById(R.id.btn_main_one2One).setOnClickListener(this);
        findViewById(R.id.btn_main_meeting).setOnClickListener(this);
        findViewById(R.id.btn_main_live).setOnClickListener(this);
        findViewById(R.id.btn_main_setting).setOnClickListener(this);
        findViewById(R.id.btn_main_class).setOnClickListener(this);
        findViewById(R.id.btn_main_audio).setOnClickListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_main_one2One:
                startActivity(new Intent(this,
                        VoipListActivity.class));
                break;
            /*case R.id.btn_main_meeting:
                startActivity(new Intent(this,VideoMeetingListActivity.class));
                break;
            case R.id.btn_main_live:
                Intent intent3 = new Intent(this, VideoLiveListActivity.class);
                startActivity(intent3);
                break;*/
            case R.id.btn_main_setting:
                Intent intent6 = new Intent(this, SettingsActivity.class);
                startActivity(intent6);
                break;
            /*case R.id.btn_main_im:
                Intent intent7= new Intent(this, IMDemoActivity.class);
                startActivity(intent7);
                break;
            case R.id.btn_main_class:
                Intent intent8= new Intent(this, MiniClassListActivity.class);
                startActivity(intent8);
                break;
            case R.id.btn_main_audio:
                Intent intent9= new Intent(this, AudioLiveListActivity.class);
                startActivity(intent9);
                break;*/
        }
    }
}
