package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.api.TaskSender;

public class OscarJobService extends JobService implements TaskSender.OneTimeListener {

    private static final int JOB_ID = 11938;    // made up number
    private JobParameters mParams;

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
        mParams = params;
        TaskSender.get().addListener(this).start(App.getApp());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        L.i("OscarJobService.onStopJob");
        return true;
    }

    @Override
    public void taskSenderPaused() {
        L.i("OJS.taskSenderPaused");
        jobFinished(mParams, false);
    }
}
