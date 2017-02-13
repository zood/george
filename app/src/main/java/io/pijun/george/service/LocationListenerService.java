package io.pijun.george.service;

import android.Manifest;
import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.HandlerThread;
import android.os.IBinder;
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

    @AnyThread
    @NonNull
    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, LocationListenerService.class);
    }

    private static Looper sServiceLooper;
    static {
        HandlerThread thread = new HandlerThread(LocationListenerService.class.getSimpleName());
        thread.start();

        sServiceLooper = thread.getLooper();
    }

    private Thread mServiceThread;
    private GoogleApiClient mGoogleClient;
//    private LocationUploadService mMonitorService;
//    private ServiceConnection mConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            LocationUploadService.LocalBinder binder = (LocationUploadService.LocalBinder) service;
//            mMonitorService = binder.getService();
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            mMonitorService = null;
//        }
//    };

    public LocationListenerService() {
        super(LocationListenerService.class.getSimpleName());
    }

    @Override
    @WorkerThread
    protected void onHandleIntent(Intent intent) {
        L.i("LLS.onHandleIntent");
//        bindService(LocationUploadService.newIntent(this), mConnection, 0);

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

            // we only try to obtain the location for 15 seconds. If we receive a high enough
            // accuracy location before this, we'll be interrupted from onLocationChanged()
            try {
                Thread.sleep(15 * DateUtils.SECOND_IN_MILLIS);
            } catch (InterruptedException ignore) {}
        } else {
            L.w("LocationListenerService ran without Location permission");
            cleanUp();
            return;
        }

//        // Upload the location
//        if (mMonitorService != null) {
//            mMonitorService.flush();
//        }

        cleanUp();
    }

    private void cleanUp() {
        if (mGoogleClient != null && mGoogleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
            mGoogleClient.disconnect();
        }

//        try {
//            unbindService(mConnection);
//        } catch (Exception ignore) {}
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
