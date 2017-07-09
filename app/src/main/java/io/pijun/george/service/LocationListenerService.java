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
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.crash.FirebaseCrash;

import io.pijun.george.App;
import io.pijun.george.L;

public class LocationListenerService extends IntentService {

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

    public LocationListenerService() {
        super(LocationListenerService.class.getSimpleName());
    }

    @Override
    @WorkerThread
    protected void onHandleIntent(Intent intent) {
        L.i("LLS.onHandleIntent");

        // This check should be useless, because the service doesn't get started until after the
        // permission is verified from MapActivity. But at least it makes Android Studio
        // stop complaining.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mServiceThread = Thread.currentThread();
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest request = new LocationRequest();
        request.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        client.requestLocationUpdates(request, mLocationCallbackHelper, sServiceLooper);

        // wait around for while we get a more precise location
        try {
            Thread.sleep(MAX_WAIT_SECONDS * DateUtils.SECOND_IN_MILLIS);
        } catch (InterruptedException ignore) {}

        client.removeLocationUpdates(mLocationCallbackHelper);
    }

    private LocationCallback mLocationCallbackHelper = new LocationCallback() {
        @Override
        @UiThread
        public void onLocationResult(LocationResult result) {
            L.i("LLS.onLocationChanged");
            Location location = result.getLastLocation();
            try {
                if (location != null) {
                    App.postOnBus(location);
                    // if we get a location with an accuracy within 10 meters, that's good enough
                    if (location.hasAccuracy() && location.getAccuracy() <= 10) {
                        mServiceThread.interrupt();
                    }
                }
            } catch (Throwable t) {
                FirebaseCrash.report(t);
            }
        }
    };
}
