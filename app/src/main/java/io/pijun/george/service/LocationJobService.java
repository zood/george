package io.pijun.george.service;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
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

public class LocationJobService extends JobService implements LocationListener {

    public static final int JOB_ID = 4319; // made up number
    private static Looper sServiceLooper;
    private static Handler sServiceHandler;
    static {
        HandlerThread thread = new HandlerThread("LocationJobService");
        thread.start();

        sServiceLooper = thread.getLooper();
        sServiceHandler = new Handler(sServiceLooper);
    }

    public static JobInfo getJobInfo(Context context) {
        // We use the minimum latency and manually reschedule the job after completion
        // because Android N has a minimum period duration of 15 minutes.
        ComponentName compName = new ComponentName(context, LocationJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(LocationJobService.JOB_ID, compName)
                .setMinimumLatency(10 * DateUtils.MINUTE_IN_MILLIS)
//                .setMinimumLatency(15 * DateUtils.SECOND_IN_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
        return builder.build();
    }

    private GoogleApiClient mGoogleClient;
    private JobParameters mJobParams;
    private LocationMonitor mMonitorService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationMonitor.LocalBinder binder = (LocationMonitor.LocalBinder) service;
            mMonitorService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMonitorService = null;
        }
    };

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("LJS.onStartJob");
        mJobParams = params;
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                performLocationJobUpdate();
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        L.i("LJS.onStopJob");
        return true;
    }

    @WorkerThread
    private void finishLocationJobUpdate() {
        L.i("marking job as finished");
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, LocationJobService.this);
        mGoogleClient.disconnect();

        // upload the location
        if (mMonitorService != null) {
            mMonitorService.flush();
            unbindService(mConnection);
        } else {
            L.w("LJS encountered null monitor when finishing job");
        }

        jobFinished(mJobParams, false);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                JobScheduler scheduler = (JobScheduler) App.getApp().getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.schedule(getJobInfo(App.getApp()));
            }
        });
    }

    @WorkerThread
    private void performLocationJobUpdate() {
        L.i("performLocationJobUpdate");
        bindService(LocationMonitor.newIntent(this), mConnection, 0);

        mGoogleClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        ConnectionResult connectionResult = mGoogleClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            L.i("|  google client connect failed");
            L.i("|  has resolution? " + connectionResult.hasResolution());
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
                    L.i("LJS.runnable");
                    finishLocationJobUpdate();
                }
            }, 10 * DateUtils.SECOND_IN_MILLIS);
        } else {
            L.w("LocationJobService ran without Location permission");
            finishLocationJobUpdate();
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
