package com.araujo.jordan.wearrow;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;

public class MainActivity extends Activity {

    private WearRotationArrow mWearRotationArrow;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mWearRotationArrow != null)
            mWearRotationArrow.stop();
    }

    private void setupViews() {
        ImageView arrowImg = (ImageView) findViewById(R.id.arrowImg);
        TextView textInfo = (TextView) findViewById(R.id.textInfo);
        LatLng mLatLng = getLocationFromIntent();
        if (mLatLng != null) {
            Log.e("ARAUJOJORDAN", "HAS LATLNG: " + mLatLng.latitude + " " + mLatLng.longitude);
            mWearRotationArrow = new WearRotationArrow(arrowImg, textInfo, mLatLng, this);
            mWearRotationArrow.start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(1000);
                            if(!mWearRotationArrow.isRunning)
                                finish();
                        } catch (InterruptedException e) {}
                    }
                }
            }).start();
        } else
            Toast.makeText(this, getResources().getString(R.string.noGEOFound), Toast.LENGTH_LONG)
                    .show();
    }

    private LatLng getLocationFromIntent() {
        LatLng latLng;
        if (getIntent().hasExtra("location")) {
            double[] values = toDoubleArray(getIntent().getExtras().getByteArray("location"));
            return new LatLng(values[0], values[1]);
        }
        return null;
    }

    private double[] toDoubleArray(byte[] byteArray) {
        int times = Double.SIZE / Byte.SIZE;
        double[] doubles = new double[byteArray.length / times];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = ByteBuffer.wrap(byteArray, i * times, times).getDouble();
        }
        return doubles;
    }
}