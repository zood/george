package io.pijun.george;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
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
import com.google.android.gms.tasks.Task;
import com.google.firebase.crash.FirebaseCrash;

import java.util.concurrent.ConcurrentHashMap;

public class LocationSeeker {

    private static final int MAX_WAIT_SECONDS = 35;
    private static int SEEKER_COUNT = 1;
    private final Looper mLooper;
    private final HandlerThread mThread;
    private final Handler mHandler;
    @Nullable private FusedLocationProviderClient client;
    @Nullable private LocationSeekerListener mListener;
    private Thread mTimeoutThread;
    private PowerManager.WakeLock mWakeLock;
    // keep LocationSeekers in here so they don't get garbage collected
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static ConcurrentHashMap<LocationSeeker, Boolean> sActiveLocationSeekers = new ConcurrentHashMap<>();

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
            L.i("shutdown about to post runnable");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _shutdown();
                }
            });
            mThread.quitSafely();
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
        try {
            mWakeLock.release();
            mWakeLock = null;
        } catch (Throwable ignore) {} // in case the lock already expired
        sActiveLocationSeekers.remove(this);
    }

    @AnyThread
    @NonNull
    public LocationSeeker start(@NonNull Context context) {
        sActiveLocationSeekers.put(this, true);
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
        Task<Void> task = client.requestLocationUpdates(request, mLocationCallbackHelper, mLooper);
        if (!task.isSuccessful()) {
            Exception ex = task.getException();
            if (ex == null) {
                FirebaseCrash.report(new RuntimeException("Unable to request location updates"));
            } else {
                FirebaseCrash.report(new RuntimeException("Unable to request location updates because of exception", ex));
            }
        }

        PowerManager pwrMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pwrMgr != null) {   // should never be null
            mWakeLock = pwrMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationSeekerLock"+SEEKER_COUNT);
            mWakeLock.acquire(MAX_WAIT_SECONDS * DateUtils.SECOND_IN_MILLIS);
        }

        // schedule a shutdown in case we can't get a precise location quickly enough
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    mTimeoutThread = Thread.currentThread();
                    Thread.sleep(MAX_WAIT_SECONDS * DateUtils.SECOND_IN_MILLIS);
                } catch (InterruptedException ignore) {
                    L.i("LocationSeeker interrupted");
                    return;
                } finally {
                    mTimeoutThread = null;
                }
                L.i("LocationSeeker timed out");
                shutdown();
            }
        });

        return this;
    }

    private LocationCallback mLocationCallbackHelper = new LocationCallback() {

        private long timeOfAccurateLocation = -1;

        @Override
        @WorkerThread
        public void onLocationResult(LocationResult result) {
            L.i("LS.onLocationResult");
            Location location = result.getLastLocation();
            try {
                if (location != null) {
                    App.postOnBus(location);
                    // if we get a location with an accuracy within 10 meters, that's good enough
                    // Also, the location needs to be from within the last 30 seconds
                    L.i("\thasAcc? " + location.hasAccuracy() + ", acc? " + location.getAccuracy() + ", time: " + location.getTime() + ", now: " + System.currentTimeMillis());
                    if (location.hasAccuracy() && location.getAccuracy() <= 10 &&
                            (System.currentTimeMillis() - location.getTime()) < 30000) {
                        // Give the device an extra 5 seconds to upload this location to the server.
                        if (timeOfAccurateLocation == -1) {
                            timeOfAccurateLocation = System.currentTimeMillis();
                        } else {
                            if (System.currentTimeMillis() - timeOfAccurateLocation > 5000) {
                                mTimeoutThread.interrupt();
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                L.w("Exception in onLocationResult", t);
                FirebaseCrash.report(t);
            }
        }
    };

    public interface LocationSeekerListener {
        @WorkerThread
        void locationSeekerFinished(@NonNull LocationSeeker seeker);
    }
}
