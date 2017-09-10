package io.pijun.george;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.crash.FirebaseCrash;

public class LocationSeeker {

    private static final int MAX_WAIT_SECONDS = 30;
    private static int SEEKER_COUNT = 1;
    private final Looper mLooper;
    private final HandlerThread mThread;
    private final Handler mHandler;
    @Nullable private FusedLocationProviderClient client;
    @Nullable private LocationSeekerListener mListener;

    @AnyThread
    public LocationSeeker() {
        mThread = new HandlerThread(LocationSeeker.class.getSimpleName() + "_" + SEEKER_COUNT++);
        mThread.start();
        mLooper = mThread.getLooper();
        mHandler = new Handler(mLooper);
    }

    @AnyThread
    @NonNull
    public LocationSeeker listener(@Nullable LocationSeekerListener l) {
        mListener = l;
        return this;
    }

    @WorkerThread
    synchronized public void shutdown() {
        L.i("LS.shutdown");
        try {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _shutdown();
                }
            });
            mThread.quit();
        } catch (Throwable t) {
            L.w("Error shutting down LocationSeeker", t);
        }
    }

    @WorkerThread
    private void _shutdown() {
        L.i("LS._shutdown");
        if (client != null) {
            client.removeLocationUpdates(mLocationCallbackHelper);
        }
        if (mListener != null) {
            mListener.locationSeekerFinished(this);
        }
    }

    @AnyThread
    @NonNull
    public LocationSeeker start(@NonNull Context context) {
        L.i("LocationSeeker.startSeeking");
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    shutdown();
                }
            });
            return this;
        }

        client = LocationServices.getFusedLocationProviderClient(context);
        LocationRequest request = new LocationRequest();
        request.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        client.requestLocationUpdates(request, mLocationCallbackHelper, mLooper);

        // schedule a shutdown in case we can't get a precise location quickly enough
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(MAX_WAIT_SECONDS * 1000);
                } catch (InterruptedException ignore) {}
                L.i("LocationSeeker time out is running");
                shutdown();
            }
        });

        return this;
    }

    private LocationCallback mLocationCallbackHelper = new LocationCallback() {
        @Override
        @WorkerThread
        public void onLocationResult(LocationResult result) {
            L.i("LS.onLocationResult");
            Location location = result.getLastLocation();
            try {
                if (location != null) {
                    App.postOnBus(location);
                    // if we get a location with an accuracy within 10 meters, that's good enough
                    L.i("\thasAcc? " + location.hasAccuracy() + ", acc? " + location.getAccuracy());
                    if (location.hasAccuracy() && location.getAccuracy() <= 10) {
                        shutdown();
                    }
                }
            } catch (Throwable t) {
                FirebaseCrash.report(t);
            }
        }
    };

    public interface LocationSeekerListener {
        @WorkerThread
        void locationSeekerFinished(@NonNull LocationSeeker seeker);
    }
}
