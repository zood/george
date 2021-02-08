package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.text.format.DateUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import io.pijun.george.App;
import io.pijun.george.AuthenticationManager;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.WorkerRunnable;
import xyz.zood.george.Permissions;

public class LocationJobService extends JobService {

    public static final int JOB_ID = 4319; // made up number

    private JobParameters mParams;

    @AnyThread
    public static void cancelLocationJobService(@NonNull Context ctx) {
        JobScheduler scheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            // should never happen
            L.w("JobScheduler was null");
            return;
        }
        scheduler.cancel(LocationJobService.JOB_ID);
    }

    public static JobInfo getJobInfo(@NonNull Context context) {
        ComponentName compName = new ComponentName(context, LocationJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(LocationJobService.JOB_ID, compName)
                .setPeriodic(15 * DateUtils.MINUTE_IN_MILLIS)
                .setPersisted(true)
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
        if (!AuthenticationManager.isLoggedIn(this)) {
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
        if (App.isInForeground || App.isLimitedShareRunning) {
            L.i("  skipping PositionService start, because App.isInForeground || App.isLimitedShareRunning");
            jobFinished(mParams, false);
            return false;
        }
        // if we already uploaded our location within the last 3 minutes - get out of here
        long timeSince = Prefs.get(this).getLastLocationUpdateTime();
        if (timeSince < 3 * DateUtils.MINUTE_IN_MILLIS) {
            jobFinished(mParams, false);
            return false;
        }

        if (!Permissions.checkBackgroundLocationPermission(this)) {
            return false;
        }

        ContextCompat.startForegroundService(this, PositionService.newIntent(this, null));
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                PositionService.await();
                L.i("LJS.locationSeekerFinished");
                jobFinished(mParams, false);
            }
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    @AnyThread
    public static void scheduleLocationJobService(@NonNull Context ctx) {
        JobScheduler scheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            // should never happen
            L.w("JobScheduler was null");
            return;
        }
        L.i("Scheduling LocationJobService");
        scheduler.schedule(LocationJobService.getJobInfo(ctx));
    }
}
