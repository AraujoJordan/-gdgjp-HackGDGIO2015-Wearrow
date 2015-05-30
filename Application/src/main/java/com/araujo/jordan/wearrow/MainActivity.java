/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.araujo.jordan.wearrow;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.SendMessageResult;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;

public class MainActivity extends Activity implements DataApi.DataListener,
        MessageApi.MessageListener, NodeApi.NodeListener, ConnectionCallbacks,
        OnConnectionFailedListener {

    private static final String TAG = "MainActivity";

    /**
     * Request code for launching the Intent to resolve Google Play services errors.
     */
    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private static final String START_ACTIVITY_PATH = "/start-activity";

    private GoogleApiClient mGoogleApiClient;
    private RotationArrow mRotationArrow;

    private LatLng mLatLng;
    private ImageView arrowImg;
    private TextView arrowText;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);
        setupViews();

        LolipopFeatures.actionAndStatusBar(this);
        try {
            ActionBar bar = getActionBar();
            bar.setElevation(0);
            bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ff5722")));
        } catch (NullPointerException error) {
            Log.e("ARAUJOJORDAN","ACTIONBAR COLOR ERROR");
        }


        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
        if(mRotationArrow != null)
            mRotationArrow.stop();
    }

    @Override //ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        LOGD(TAG, "Google API Client was connected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.addListener(mGoogleApiClient, this);

        onStartWearableActivity();
    }

    @Override //ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        LOGD(TAG, "Connection to Google API client was suspended");
    }

    @Override //OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (result.hasResolution()) {
            try {
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                mGoogleApiClient.connect();
            }
        } else {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        }
    }

    @Override //DataListener
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged: " + dataEvents);

    }

    @Override //MessageListener
    public void onMessageReceived(final MessageEvent messageEvent) {
        LOGD(TAG, "onMessageReceived() A message from watch was received:" + messageEvent
                .getRequestId() + " " + messageEvent.getPath());
    }

    @Override //NodeListener
    public void onPeerConnected(final Node peer) {
        LOGD(TAG, "onPeerConnected: " + peer);
        onStartWearableActivity();

    }

    @Override //NodeListener
    public void onPeerDisconnected(final Node peer) {
        LOGD(TAG, "onPeerDisconnected: " + peer);
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }

        return results;
    }

    private void sendStartActivityMessage(String node) {

        double[] location = new double[2];
        location[0] = mLatLng.latitude;
        location[1] = mLatLng.longitude;

        Wearable.MessageApi.sendMessage(
                mGoogleApiClient, node, START_ACTIVITY_PATH, toByteArray(location))
                .setResultCallback(new ResultCallback<SendMessageResult>() {
                                       @Override
                                       public void onResult(SendMessageResult sendMessageResult) {
                                           if (!sendMessageResult.getStatus().isSuccess()) {
                                               Log.e(TAG, "Failed to send message with status code: "
                                                       + sendMessageResult.getStatus().getStatusCode());
                                           }
                                       }
                                   }
                );
    }

    /**
     * Sends an RPC to start a fullscreen Activity on the wearable.
     */
    public void onStartWearableActivity() {
        if (mGoogleApiClient.isConnected())
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Collection<String> nodes = getNodes();
                    for (String node : nodes) {
                        sendStartActivityMessage(node);
                    }
                }
            }).start();
    }

    /**
     * Sets up UI components and their callback handlers.
     */
    private void setupViews() {
        arrowText = (TextView) findViewById(R.id.phone_arrowText);
        arrowImg = (ImageView) findViewById(R.id.phone_arrowImg);

        mLatLng = getPointLocationFromIntent();

        mRotationArrow = new RotationArrow(arrowImg, arrowText, mLatLng, this);
        mRotationArrow.start();
    }

    /**
     * As simple wrapper around Log.d
     */
    private static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    /**
     * Return the location from an outside activity
     *
     * @author Jordan Junior
     * @version 1.0
     */
    private LatLng getPointLocationFromIntent() {
        LatLng returningPosition = new LatLng(0d, 0d);

        try {
            if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
                Uri geoUri = getIntent().getData();
                String scheme = geoUri.getScheme();
                String host = geoUri.getHost();
                if (scheme.equals("geo") || host.equals("maps.google.com")) {

                    double lat = Double.valueOf(geoUri.getQuery().split("loc:")[1].split(",")[0]);
                    double lon = Double.valueOf(geoUri.getQuery().split(",")[1].split(" ")[0]);

                    returningPosition = new LatLng(lat, lon);

                } else
                    throw new IllegalArgumentException(getResources().getString(R.string.noGEOFound));
            } else
                throw new IllegalArgumentException(getResources().getString(R.string.noGEOFound));
        } catch (Exception error) {
            Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
        return returningPosition;
    }

    public static byte[] toByteArray(double[] doubleArray) {
        int times = Double.SIZE / Byte.SIZE;
        byte[] bytes = new byte[doubleArray.length * times];
        for (int i = 0; i < doubleArray.length; i++) {
            ByteBuffer.wrap(bytes, i * times, times).putDouble(doubleArray[i]);
        }
        return bytes;
    }

}
