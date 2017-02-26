package io.pijun.george.service;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import io.pijun.george.App;
import io.pijun.george.L;

public class LocationListenerService extends IntentService implements LocationListener {

    private static final int MAX_WAIT_SECONDS = 30;

    @AnyThread
    @NonNull
    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, LocationListenerService.class);
    }

    public static Looper sServiceLooper;
    static {
        HandlerThread thread = new HandlerThread(LocationListenerService.class.getSimpleName());
        thread.start();

        sServiceLooper = thread.getLooper();
    }

    private Thread mServiceThread;
    private GoogleApiClient mGoogleClient;

    public LocationListenerService() {
        super(LocationListenerService.class.getSimpleName());
    }

    @Override
    @WorkerThread
    protected void onHandleIntent(Intent intent) {
        L.i("LLS.onHandleIntent");
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        ConnectionResult connectionResult = mGoogleClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            L.i("  google client connect failed: " + connectionResult.getErrorMessage());
            L.i("  has resolution? " + connectionResult.hasResolution());
            cleanUp();
            return;
        }

        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleClient);
        if (lastLocation != null) {
            App.postOnBus(lastLocation);
        }

        LocationRequest req = LocationRequest.create();
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        req.setInterval(5 * DateUtils.SECOND_IN_MILLIS);

        mServiceThread = Thread.currentThread();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleClient, req, this, sServiceLooper);

            // We only try to obtain the location for a short while. If we receive a high enough
            // accuracy location before this, we'll be interrupted from onLocationChanged()
            try {
                Thread.sleep(MAX_WAIT_SECONDS * DateUtils.SECOND_IN_MILLIS);
            } catch (InterruptedException ignore) {}
        } else {
            L.w("LocationListenerService ran without Location permission");
            cleanUp();
            return;
        }

        cleanUp();
    }

    private void cleanUp() {
        if (mGoogleClient != null && mGoogleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
            mGoogleClient.disconnect();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            App.postOnBus(location);
            // if we get a location with an accuracy within 10 meters, that's good enough
            if (location.hasAccuracy() && location.getAccuracy() <= 10) {
                mServiceThread.interrupt();
            }
        }
    }
}
