package io.pijun.george.service;

import android.app.IntentService;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.squareup.tape.FileObjectQueue;

import java.io.IOException;

import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.task.DeleteMessageTask;
import io.pijun.george.api.task.OscarTask;
import io.pijun.george.api.task.SendMessageTask;
import retrofit2.Call;
import retrofit2.Response;

public class OscarTasksService extends IntentService {

    public static Intent newIntent(Context context) {
        return new Intent(context, OscarTasksService.class);
    }

    public OscarTasksService() {
        super(OscarTasksService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        L.i("OscarTasksService.onHandleIntent");
        FileObjectQueue<OscarTask> mQueue = OscarClient.getQueue(this);

        // make sure we're still logged in
        String token = Prefs.get(this).getAccessToken();
        if (TextUtils.isEmpty(token)) {
            // not logged in, so empty the queue and get out of here
            while (mQueue.size() > 0) {
                mQueue.remove();
            }
            return;
        }

        OscarAPI api = OscarClient.newInstance(token);
        while (mQueue.size() > 0) {
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
                // a connection problem, so schedule this to continue when the network is back
                JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.schedule(OscarJobService.getJobInfo(this));
                return;
            }
            L.i("|  end processQueue loop");
        }
    }

}
