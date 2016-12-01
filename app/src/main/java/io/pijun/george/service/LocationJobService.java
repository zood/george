package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.text.format.DateUtils;

import io.pijun.george.L;
import io.pijun.george.Prefs;

public class LocationJobService extends JobService {

    public static final int JOB_ID = 4319; // made up number

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

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("LJS.onStartJob");
        // If we're not logged in, just get out of here. This also makes sure we don't get rescheduled.
        if (!Prefs.get(this).isLoggedIn()) {
            jobFinished(params, false);
            return false;
        }

        startService(LocationListenerService.newIntent(this));

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        L.i("LJS.onStopJob");
        return false;
    }
}
