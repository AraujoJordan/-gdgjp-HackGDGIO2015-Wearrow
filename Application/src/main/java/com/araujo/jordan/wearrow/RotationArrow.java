package com.araujo.jordan.wearrow;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Created by araujojordan on 18/05/15.
 */
public class RotationArrow {

    private JLocationListener jLocationListener;
    private JSensorEventListener jSensorEventListener;

    private TextView textInfo; //The  text of distance (and loading text)
    private ImageView arrowImg; //the arrow image (that rotate with setRotation method)

    private LatLng destinationLocation, userLocation;

    private Activity act;

    public RotationArrow(ImageView arrowImg, TextView textInfo, LatLng destinationLocation, Activity act) {
        this.act = act;
        this.arrowImg = arrowImg;
        this.textInfo = textInfo;

        jLocationListener = new JLocationListener(act);
        jSensorEventListener = new JSensorEventListener();

        userLocation = new LatLng(0, 0); //Will change when GPS get user position
        this.destinationLocation = destinationLocation;
    }

    public void start() {
        jLocationListener.startUsingGPS();
        jSensorEventListener.startUsingGiro();
    }

    public void stop() {
        jSensorEventListener.stopUsingGiro();
        jLocationListener.stopUsingGPS();
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
    private class JLocationListener implements LocationListener, GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener,
            NodeApi.NodeListener {

        private final int TWO_MINUTES = 1000 * 60 * 2;
        private final Context mContext;
        protected LocationManager locationManager;
        private Location location;

        private LinkedList<String> nodesid;

        private GoogleApiClient mGoogleApiClient;

        private boolean isRunning;

        public JLocationListener(Activity act) {
            this.mContext = act;
            locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            isRunning = false;

            mGoogleApiClient = new GoogleApiClient.Builder(act)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();

            nodesid = new LinkedList<>();
        }

        /**
         * Stop using GPS listener Calling this function will stop using GPS in your app
         */
        public void stopUsingGPS() {
            if (!isRunning)
                return;

            if (locationManager != null) {
                locationManager.removeUpdates(JLocationListener.this);
            }
            isRunning = false;

            if (mGoogleApiClient.isConnected()) {
                Log.v("ARAUJOJORDAN", "GAC está ON, enviando sinal de parada...");
                getNodes();

                for (String node : nodesid) {
                    Log.v("ARAUJOJORDAN", "Enviando para dispositivo " + node);
                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, node, "/stop_sending_position",
                            new byte[0]).setResultCallback(
                            new ResultCallback<MessageApi.SendMessageResult>() {
                                @Override
                                public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                    if (sendMessageResult.getStatus().isSuccess()) {
                                        Log.v("ARAUJOJORDAN", "Sinal de parada enviado!");
                                    } else
                                        Log.v("ARAUJOJORDAN", "Falha no envio!");
                                }
                            }
                    );
                }

            }

        }

        public void startUsingGPS() {

            Log.v("ARAUJOJORDAN", "starting using gps");

            isRunning = true;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                Log.v("ARAUJOJORDAN", "GPS IS ON");
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
                return;
            }
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);
                return;
            }

            Toast.makeText(act.getApplicationContext(), act.getResources().getString(R.string.noGpsFound), Toast.LENGTH_LONG).show();
        }

        /**
         * Function to get latitude
         */
        public double getLatitude() {
            if (location != null) {
                return location.getLatitude();
            }
            return 0.0;
        }

        /**
         * Function to get longitude
         */
        public double getLongitude() {
            if (location != null) {
                return location.getLongitude();
            }
            return 0.0;
        }

        /**
         * Update the userLocation with the new location
         */
        @Override
        public void onLocationChanged(Location newLocation) {
            Log.v("ARAUJOJORDAN", "Nova posicao do GPS");
            if (isBetterLocation(newLocation, this.location)) {
                this.location = newLocation;
                userLocation = new LatLng(getLatitude(), getLongitude());
                if (arrowImg != null) {
                    textInfo.setText(getDistanceBetween2Points(userLocation, destinationLocation));
                }

                final double[] position = new double[2];
                position[0] = newLocation.getLatitude();
                position[1] = newLocation.getLongitude();

                if (mGoogleApiClient.isConnected()) {
                    Log.v("ARAUJOJORDAN", "GAC está ON, enviando ponto...");
                    getNodes();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (nodesid.isEmpty())
                                try {
                                    Thread.sleep(2000);
                                    getNodes();
                                } catch (InterruptedException e) {
                                }
                            sendPosition(position);

                        }
                    }).start();

                }

            }
        }

        private void sendPosition(double[] position) {
            for (String node : nodesid) {
                Log.v("ARAUJOJORDAN", "Enviando para dispositivo " + node);
                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient, node, "/gps_from_handheld",
                        toByteArray(position)).setResultCallback(
                        new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess()) {
                                    Log.v("ARAUJOJORDAN", "Ponto enviado!");
                                } else
                                    Log.v("ARAUJOJORDAN", "Ponto NÃO enviado!");
                            }
                        }
                );
            }
        }

        private Collection<String> getNodes() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    LinkedList<String> lnodes = new LinkedList<String>();
                    NodeApi.GetConnectedNodesResult nodes =
                            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    for (Node node : nodes.getNodes())
                        lnodes.add(node.getId());
                    nodesid = lnodes;
                }
            }).start();
            return nodesid;
        }


        public byte[] toByteArray(double[] doubleArray) {
            int times = Double.SIZE / Byte.SIZE;
            byte[] bytes = new byte[doubleArray.length * times];
            for (int i = 0; i < doubleArray.length; i++) {
                ByteBuffer.wrap(bytes, i * times, times).putDouble(doubleArray[i]);
            }
            return bytes;
        }


        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.v("ARAUJOJORDAN", "Alterando provedor: " + provider + " " + status);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.v("ARAUJOJORDAN", "Ativando provedor: " + provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.v("ARAUJOJORDAN", "Desativando provedor: " + provider);
            stopUsingGPS();
        }

        /**
         * Determines whether one Location reading is better than the current Location fix
         *
         * @param newLoc The new Location that you want to evaluate
         * @param oldLoc The current Location fix, to which you want to compare the new one
         */
        protected boolean isBetterLocation(Location newLoc, Location oldLoc) {
            if (oldLoc == null) {
                return true;
            }
            if (newLoc == null) {
                return false;
            }

            long timeDelta = newLoc.getTime() - oldLoc.getTime();
            boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
            boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
            boolean isNewer = timeDelta > 0;

            if (isSignificantlyNewer) {
                return true;
            } else if (isSignificantlyOlder) {
                return false;
            }
            int accuracyDelta = (int) (newLoc.getAccuracy() - oldLoc.getAccuracy());
            boolean isLessAccurate = accuracyDelta > 0;
            boolean isMoreAccurate = accuracyDelta < 0;
            boolean isSignificantlyLessAccurate = accuracyDelta > 200;
            boolean isFromSameProvider = isSameProvider(newLoc.getProvider(),
                    oldLoc.getProvider());

            if (isMoreAccurate) {
                return true;
            } else if (isNewer && !isLessAccurate) {
                return true;
            } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                return true;
            }
            return false;
        }

        private boolean isSameProvider(String provider1, String provider2) {
            if (provider1 == null) {
                return provider2 == null;
            }
            return provider1.equals(provider2);
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
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {

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

        private boolean isRunning;

        private SensorManager mSensorManager;
        private Sensor mSensor, aSensor;

        public JSensorEventListener() {
            mSensorManager = (SensorManager) act.getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            aSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            isRunning = false;
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
            if (!isRunning)
                return;

            mSensorManager.unregisterListener(this);
            isRunning = false;
        }

        public void startUsingGiro() {
            if (isRunning)
                stopUsingGiro();

            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, aSensor, SensorManager.SENSOR_DELAY_UI);
            isRunning = true;

        }
    }
}