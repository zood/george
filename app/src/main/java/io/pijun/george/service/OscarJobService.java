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

    /*
    @WorkerThread
    private void processQueue() {
        mQueue = OscarClient.getQueue(this);

        // make sure we're still logged in
        String token = Prefs.get(this).getAccessToken();
        if (TextUtils.isEmpty(token)) {
            // not logged in, so empty the queue and get out of here
            while (mQueue.size() > 0) {
                mQueue.remove();
            }
            jobFinished(mParams, false);
            return;
        }

        OscarAPI api = OscarClient.newInstance(token);

        while (mQueue.size() > 0) {
            if (mShouldStop) {
                return;
            }

            OscarTask task = mQueue.peek();
            Call call;
            L.i("processQueue: peeked " + task);
            switch (task.apiMethod) {
                case DeleteMessageTask.NAME:
                    L.i("|  casting to DeleteMessageTask");
                    DeleteMessageTask dmt = (DeleteMessageTask) task;
                    call = api.deleteMessage(dmt.messageId);
                    break;
                case SendMessageTask.NAME:
                    L.i("|  casting to SendMessageTask");
                    SendMessageTask smt = (SendMessageTask) task;
                    call = api.sendMessage(smt.hexUserId, smt.message);
                    break;
                default:
                    throw new RuntimeException("Unknown task type");
            }
            try {
                L.i("|  about to execute");
                Response response = call.execute();
                if (response.isSuccessful()) {
                    L.i("|  successfully executed oscar task");
                    mQueue.remove();
                } else {
                    OscarError err = OscarError.fromResponse(response);
                    L.i("|  problem executing task: " + call.request().method() + " " + call.request().url());
                    L.i("|  " + err);
                }
            } catch (IOException e) {
                L.w("|  task exception", e);
            }
            L.i("|  end processQueue loop");
        }
    }
    */
}
