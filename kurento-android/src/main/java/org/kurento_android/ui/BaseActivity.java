package org.kurento_android.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import android.util.Log;
import butterknife.ButterKnife;

import de.greenrobot.event.EventBus;

/**
 * Created by cxm on 7/31/16.
 */
public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Log.i(TAG, "onCreate: " + this);
    }

    @Override
    public void setContentView(int id) {
        super.setContentView(id);
        ButterKnife.inject(this);
    }

    @Override
    public void onStart() {
        Log.i(TAG, "onStart: " + this);
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        Log.i(TAG, "onStop: " + this);
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume: " + this);
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: " + this);
    }

    public static class EventDummy {

    }

    @SuppressWarnings("unused")
    public void onEvent(EventDummy eventDummy) {

    }
}
