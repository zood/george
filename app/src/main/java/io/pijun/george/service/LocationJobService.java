package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.format.DateUtils;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.LocationSeeker;
import io.pijun.george.Prefs;

public class LocationJobService extends JobService implements LocationSeeker.LocationSeekerListener {

    public static final int JOB_ID = 4319; // made up number

    private LocationSeeker mSeeker;
    private JobParameters mParams;

    public static JobInfo getJobInfo(Context context) {
        ComponentName compName = new ComponentName(context, LocationJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(LocationJobService.JOB_ID, compName)
                .setPeriodic(10 * DateUtils.MINUTE_IN_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
        return builder.build();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("LJS.onStartJob");
        mParams = params;
        // If we're not logged in, cancel the job, then get out of here.
        if (!Prefs.get(this).isLoggedIn()) {
            jobFinished(params, false);

            L.i("LJS.onStartJob cancelling because not logged in");
            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return false;
            }
            scheduler.cancel(LocationJobService.JOB_ID);
            return false;
        }

        // only launch the service if the app isn't already in the foreground
        if (App.isInForeground) {
            L.i("  skipping LocationSeeker start, because app is in foreground");
            return false;
        }

        mSeeker = new LocationSeeker().listener(this).start(this);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mSeeker.shutdown();
        return true;
    }

    @Override
    @WorkerThread
    public void locationSeekerFinished(@NonNull LocationSeeker seeker) {
        jobFinished(mParams, false);
    }
}
