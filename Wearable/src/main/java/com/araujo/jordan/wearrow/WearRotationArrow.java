package com.araujo.jordan.wearrow;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by araujojordan on 18/05/15.
 */
public class WearRotationArrow {

    private final static String GPSPATH = "/gps_from_handheld";

    private JWearLocationListener jWearLocationListener;
    private JSensorEventListener jSensorEventListener;

    private GoogleApiClient mGoogleApiClient;

    private TextView textInfo; //The  text of distance (and loading text)
    private ImageView arrowImg; //the arrow image (that rotate with setRotation method)

    private LatLng destinationLocation, userLocation;

    public boolean isRunning;

    private Activity act;

    public WearRotationArrow(ImageView arrowImg, TextView textInfo, LatLng destinationLocation, Activity act) {
        isRunning = false;
        this.act = act;
        this.arrowImg = arrowImg;
        this.textInfo = textInfo;

        jWearLocationListener = new JWearLocationListener(act);
        jSensorEventListener = new JSensorEventListener();

        userLocation = new LatLng(0, 0); //Will change when GPS get user position
        this.destinationLocation = destinationLocation;
    }

    public void start() {
        isRunning = true;
        jWearLocationListener.startUsingGPS();
        jSensorEventListener.startUsingGiro();
    }

    public void stop() {
        isRunning = false;
        jWearLocationListener.stopUsingGPS();
        jSensorEventListener.stopUsingGiro();
    }

    /**
     * Distance between 2 location on latitude and longitude.
     *
     * @return an text indicating the distance in meters or in kilometers, if not initialized,
     * it will disapear. If its < 5m it will show and message telling that is too close
     * @author Jordan Junior
     * @version 1.0
     */
    private String getDistanceBetween2Points(LatLng p1, LatLng p2) {
        if ((p1.latitude == 0 && p1.longitude == 0) || (p2.latitude == 0 && p2.longitude == 0)) {
            if (arrowImg != null) {
                arrowImg.setVisibility(View.GONE);
            }
            return act.getResources().getString(R.string.loading);
        } else {
            if (arrowImg != null) {
                arrowImg.setVisibility(View.VISIBLE);
            }
        }

        final int R = 6371;
        final double dLat = deg2rad(p2.latitude - p1.latitude);
        final double dLon = deg2rad(p2.longitude - p1.longitude);
        final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(deg2rad(p1.latitude)) * Math.cos(deg2rad(p2.latitude)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return getMlOrKm(R * c);
    }

    private String getMlOrKm(double d) {
        String kil = act.getString(R.string.kilometer);
        String met = act.getString(R.string.meter);
        String toClose = act.getString(R.string.distanceToClose);

        if (d < 1) {
            int distance = (int) (d * 1000);
            int CLOSE_DISTANCE = 5;
            if (distance < CLOSE_DISTANCE) {
                if (arrowImg != null) {
                    arrowImg.setVisibility(View.GONE);
                }
                return toClose;
            }
            return distance + " " + met;
        } else {
            return ((int) (d)) + " " + kil; //in km
        }
    }

    /**
     * Simple conversion from degree to radian
     *
     * @author Jordan Junior
     * @version 1.0
     */
    private double deg2rad(double deg) {
        return deg * (Math.PI / 180);
    }

    /**
     * Get the angle between the 2 points using pitagoras
     *
     * @return the angle between the 2 points
     * @author Jordan Junior
     * @version 1.0
     */
    private float getAngleBetween2Points(LatLng p1, LatLng p2) {
        double deltaY = p2.latitude - p1.latitude;
        double deltaX = p2.longitude - p1.longitude;
        double result = (Math.atan2(deltaY, deltaX)) * (180 / Math.PI);
        if (result < 0) {
            return (float) (180 + (180 + result));
        }
        return (float) result;
    }


    /**
     * Listener for the Location This will update the GPS coordinates of the user
     *
     * @author Jordan Junior
     * @version 1.0
     */
    private class JWearLocationListener extends AsyncTask<Void, LatLng, Void> implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener,
            NodeApi.NodeListener {

        private boolean isLocalRunning;

        private LatLng mLocalLatLng;

        public JWearLocationListener(Activity act) {
            mGoogleApiClient = new GoogleApiClient.Builder(act)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        }

        /**
         * Stop using GPS listener Calling this function will stop using GPS listener in your wear
         */
        public void stopUsingGPS() {
            if (!isLocalRunning)
                return;

            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();

            isLocalRunning = false;
        }

        /**
         * Start using from GPS mobile, calling this function will start the GPS listener from the handheld
         */
        public void startUsingGPS() {
            if (isLocalRunning) {
                stopUsingGPS();
                return;
            }

            isLocalRunning = true;
            execute();
        }

        @Override
        protected synchronized Void doInBackground(Void... params) {
            while (isLocalRunning) {
                try {
                    wait(); //will stop until a new position from mobile
                } catch (InterruptedException returnExecution) {
                    if (mLocalLatLng.latitude != destinationLocation.latitude || //new point
                            mLocalLatLng.longitude != destinationLocation.longitude)
                        publishProgress(mLocalLatLng);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(LatLng... loc) {
            super.onProgressUpdate(loc);
            Log.v("PhoneLocationListener", "Updating user position: " + loc[0].latitude + " " + loc[0].longitude);
            userLocation = loc[0];

            textInfo.setText(getDistanceBetween2Points(userLocation, destinationLocation));
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            stopUsingGPS();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            Wearable.MessageApi.addListener(mGoogleApiClient, this);
            Wearable.NodeApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v("PhoneLocationListener", "Data changed");
            final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
            dataEvents.close();
            for (DataEvent event : events) {
                String path = event.getDataItem().getUri().getPath();
                if (GPSPATH.equals(path)) {
                    Log.v("PhoneLocationListener", "New Position");
                }
            }
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            Log.v("PhoneLocationListener", "New received position");
            if (messageEvent.getPath().equals(GPSPATH))
                updateLocation(toDoubleArray(messageEvent.getData()));
            if(messageEvent.getPath().equals("/stop_sending_position")) {
                Log.v("ARAUJOJORDA","Stop sending position signal received");
                isRunning = false;
                stop();
            }
        }

        private synchronized void updateLocation(double[] doubles) {
            Log.v("ARAUJOJORDAN","Ponto recebido: "+doubles[0]+" "+doubles[1]);

            if (mLocalLatLng == null)
                this.mLocalLatLng = new LatLng(doubles[0], doubles[1]);
            else {
                mLocalLatLng.latitude = doubles[0];
                mLocalLatLng.longitude = doubles[1];
            }

            userLocation = mLocalLatLng;
            if (mLocalLatLng.latitude != destinationLocation.latitude || //new point
                    mLocalLatLng.longitude != destinationLocation.longitude)
                publishProgress(mLocalLatLng);
        }

        @Override
        public void onPeerConnected(Node node) {

        }

        @Override
        public void onPeerDisconnected(Node node) {

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

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

    /**
     * Listener for the SensorEvent This will update the direction of the user
     *
     * @author Jordan Junior
     * @version 1.0
     */
    private class JSensorEventListener implements SensorEventListener {

        private float[] mGravity;
        private float[] mGeomagnetic;
        private float azimut;

        private boolean isLocalRunning;

        private SensorManager mSensorManager;
        private Sensor mSensor, aSensor;

        public JSensorEventListener() {
            mSensorManager = (SensorManager) act.getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            aSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            isLocalRunning = false;
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mGravity = event.values;
            }
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagnetic = event.values;
            }

            if (mGravity != null && mGeomagnetic != null) {
                float R[] = new float[9];
                float I[] = new float[9];

                if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {

                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    azimut = orientation[0];
                    float newNorth = (float) (-azimut * 360 / (2 * Math.PI));
                    float northRotation = newNorth + 30;
                    if (arrowImg != null) {
                        arrowImg.setRotation(northRotation - getAngleBetween2Points(userLocation,
                                destinationLocation
                        ));
                    }
                }
            }
        }

        public void stopUsingGiro() {
            if (!isLocalRunning)
                return;

            mSensorManager.unregisterListener(this);
            isLocalRunning = false;
        }

        public void startUsingGiro() {
            if (isLocalRunning)
                stopUsingGiro();

            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, aSensor, SensorManager.SENSOR_DELAY_UI);
            isLocalRunning = true;

        }
    }
}