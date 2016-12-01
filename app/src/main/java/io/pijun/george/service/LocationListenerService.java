package io.pijun.george.service;

import android.Manifest;
import android.app.Service;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
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
import io.pijun.george.WorkerRunnable;

public class LocationListenerService extends Service implements LocationListener {

    private static Looper sServiceLooper;
    private static Handler sServiceHandler;
    static {
        HandlerThread thread = new HandlerThread(LocationListenerService.class.getSimpleName());
        thread.start();

        sServiceLooper = thread.getLooper();
        sServiceHandler = new Handler(sServiceLooper);
    }

    @AnyThread
    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, LocationListenerService.class);
    }

    private GoogleApiClient mGoogleClient;
    private LocationUploadService mMonitorService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationUploadService.LocalBinder binder = (LocationUploadService.LocalBinder) service;
            mMonitorService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            L.i("LLS.onServiceDisconnected");
            mMonitorService = null;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    @UiThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Schedule the job service right away, in case this service gets killed before it has time
        // to schedule it.
        JobScheduler scheduler = (JobScheduler) App.getApp().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(LocationJobService.getJobInfo(this));

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                performLocationJobUpdate();
            }
        });
        return START_STICKY;
    }

    @WorkerThread
    private void finishLocationJobUpdate(boolean finishNormally) {
        L.i("LLS.finishLocationJobUpdate");

        if (mGoogleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
            mGoogleClient.disconnect();
        }

        // Upload the location
        if (finishNormally && mMonitorService != null) {
            mMonitorService.flush();
        }
        try {
            unbindService(mConnection);
        } catch (Exception ignore) {}

        // reschedule the job, now that we're done
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                JobScheduler scheduler = (JobScheduler) App.getApp().getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.schedule(LocationJobService.getJobInfo(LocationListenerService.this));
            }
        });

        stopSelf();
    }

    @WorkerThread
    private void performLocationJobUpdate() {
        L.i("performLocationJobUpdate");
        bindService(LocationUploadService.newIntent(this), mConnection, 0);

        mGoogleClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        ConnectionResult connectionResult = mGoogleClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            L.i("|  google client connect failed");
            L.i("|  has resolution? " + connectionResult.hasResolution());
            finishLocationJobUpdate(false);
            return;
        }

        LocationRequest req = LocationRequest.create();
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        req.setInterval(5 * DateUtils.SECOND_IN_MILLIS);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleClient, req, this, sServiceLooper);

            // we only try to obtain the location for 10 seconds
            sServiceHandler.postDelayed(new WorkerRunnable() {
                @Override
                public void run() {
                    finishLocationJobUpdate(true);
                }
            }, 10 * DateUtils.SECOND_IN_MILLIS);
        } else {
            L.w("LocationListenerService ran without Location permission");
            finishLocationJobUpdate(false);
        }
    }

    @Override
    @WorkerThread
    public void onLocationChanged(Location location) {
        if (location != null) {
            App.postOnBus(location);
        }
    }
}
