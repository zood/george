package io.pijun.george.service;

import android.app.Service;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.squareup.otto.Subscribe;

import java.util.LinkedList;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.WorkerRunnable;

public class LocationMonitor extends Service {

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, LocationMonitor.class);
    }

    private static Handler sServiceHandler;
    static {
        HandlerThread thread = new HandlerThread("LocationMonitor");
        thread.start();

        Looper looper = thread.getLooper();
        sServiceHandler = new Handler(looper);
    }

    public class LocalBinder extends Binder {
        LocationMonitor getService() {
            return LocationMonitor.this;
        }
    }

//    private Location mLastLocation;
    private LinkedList<Location> mLocations = new LinkedList<>();
    private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        L.i("LM.onBind " + this);

        return mBinder;
    }

    @Override
    @UiThread
    public void onCreate() {
        super.onCreate();

        L.i("LM.onCreate " + this);
        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(LocationJobService.getJobInfo(this));

        App.registerOnBus(this);
    }

    @Override
    @UiThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        L.i("LM.onStartCommand " + this);
        return START_STICKY;
    }

    @Override
    @UiThread
    public void onDestroy() {
        L.i("LM.onDestroy " + this);
        App.unregisterFromBus(this);

        super.onDestroy();
    }

    @Subscribe
    public void onLocationChanged(final Location l) {
        sServiceHandler.post(new WorkerRunnable() {
            @Override
            public void run() {
                // check if this is a duplicate location
                if (!mLocations.isEmpty() && mLocations.getLast().getElapsedRealtimeNanos() == l.getElapsedRealtimeNanos()) {
                    return;
                }

                L.i("LM.onLocationChanged - " + l);
                mLocations.add(l);
            }
        });
    }

    @WorkerThread
    void flush() {
        L.i("LM.flush");
    }
}
