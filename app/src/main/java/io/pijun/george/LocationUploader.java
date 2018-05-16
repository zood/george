package io.pijun.george;

import android.content.Context;
import android.location.Location;
import android.support.annotation.AnyThread;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.squareup.otto.Subscribe;

import java.util.concurrent.LinkedBlockingQueue;

public class LocationUploader {

    private volatile static LocationUploader sSingleton;
    private LinkedBlockingQueue<Location> mLocations = new LinkedBlockingQueue<>();

    private LocationUploader() {
    }

    /**
     * Get the most recent location and report it.
     */
    @WorkerThread
    private void flush() {
        // If we have no location to report, just get out of here.
        if (mLocations.isEmpty()) {
            L.i("LU.flush: no location to flush");
            return;
        }

        Context ctx = App.getApp();
        if (!AuthenticationManager.isLoggedIn(ctx)) {
            mLocations.clear();
            return;
        }

        Location location = mLocations.peek();
        if (location == null) {
            // another call to flush could have raced us to the last location
            return;
        }

        LocationUtils.upload(ctx, location, false);
        mLocations.clear();
    }

    @AnyThread
    @NonNull
    public static LocationUploader get() {
        if (sSingleton == null) {
            synchronized (LocationUploader.class) {
                if (sSingleton == null) {
                    sSingleton = new LocationUploader();
                }
            }
        }
        return sSingleton;
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationChanged(final Location l) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                // check if this is a duplicate location
                if (!mLocations.isEmpty() && mLocations.peek().getElapsedRealtimeNanos() == l.getElapsedRealtimeNanos()) {
                    L.i("\tduplicate location");
                    return;
                }

                mLocations.add(l);
                flush();
            }
        });
    }
}
