package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import io.pijun.george.L;

public class MessageQueueJobService extends JobService {

    private static final int JOB_ID = 49171;    // made up number

    public static JobInfo getJobInfo(Context context) {
        ComponentName compName = new ComponentName(context, MessageQueueJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, compName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
        return builder.build();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("MessageQueueJobService.onStartJob");
        Intent i = MessageQueueService.newIntent(this);
        startService(i);

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
