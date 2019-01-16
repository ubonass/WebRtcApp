package org.ubonass.voip;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import org.ubonass.AppManager;
import org.ubonass.BaseActivity;
import org.ubonass.R;
import org.ubonass.database.CoreDataBase;
import org.ubonass.database.HistoryBean;
import org.ubonass.ui.CircularCoverView;
import org.ubonass.utils.ColorUtils;
import org.ubonass.utils.DensityUtils;

import java.text.SimpleDateFormat;

public class VoipRingingActivity extends BaseActivity implements View.OnClickListener {

    private String targetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_voip_ringing);

        targetId = getIntent().getStringExtra("targetId");
        findViewById(R.id.ring_hangoff).setOnClickListener(this);
        findViewById(R.id.ring_pickup).setOnClickListener(this);
        findViewById(R.id.ring_pickup_audio).setOnClickListener(this);
        ((TextView) findViewById(R.id.targetid_text)).setText(targetId);
        findViewById(R.id.head_bg).setBackgroundColor(ColorUtils.getColor(VoipRingingActivity.this, targetId));
        ((CircularCoverView) findViewById(R.id.head_cover)).setCoverColor(Color.parseColor("#000000"));
        int cint = DensityUtils.dip2px(VoipRingingActivity.this, 45);
        ((CircularCoverView) findViewById(R.id.head_cover)).setRadians(cint, cint, cint, cint, 0);

        HistoryBean historyBean = new HistoryBean();
        historyBean.setType(CoreDataBase.HISTORY_TYPE_VOIP);
        historyBean.setLastTime(new SimpleDateFormat("MM-dd HH:mm")
                .format(new java.util.Date()));
        historyBean.setConversationId(targetId);
        historyBean.setNewMsgCount(1);
        AppManager.setHistory(historyBean, true);

    }


    @Override
    public void onRestart() {
        super.onRestart();

    }

    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ring_hangoff:

                break;
            case R.id.ring_pickup: {

                break;
            }
            case R.id.ring_pickup_audio: {

                break;
            }
        }
    }
}
