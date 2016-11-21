package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import io.pijun.george.L;

public class OscarJobService extends JobService {

    private static final int JOB_ID = 11938;    // made up number

    public static JobInfo getJobInfo(Context context) {
        ComponentName compName = new ComponentName(context, OscarJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, compName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
        return builder.build();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("OscarJobService.onStartJob");
        // We do the actual work in an IntentService, so there's only ever one processor of the
        // message queue. The reason we get the JobService involved at all, is so the IntentService
        // only gets started when we actually have a network connection.
        Intent i = OscarTasksService.newIntent(this);
        startService(i);

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

}
