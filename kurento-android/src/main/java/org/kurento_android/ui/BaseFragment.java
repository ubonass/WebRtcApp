package org.kurento_android.ui;

import android.support.v4.app.Fragment;

import android.util.Log;

import de.greenrobot.event.EventBus;

/**
 * Created by cxm on 7/31/16.
 */
public class BaseFragment extends Fragment {
    private static final String TAG = "BaseFragment";

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG,"onStart: " + this);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        Log.i(TAG,"onStop: " + this);
        super.onStop();
    }

    @SuppressWarnings("unused")
    public void onEvent(BaseActivity.EventDummy eventDummy) {

    }
}
