package io.pijun.george.service;

import android.app.IntentService;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.squareup.tape.FileObjectQueue;

import java.io.IOException;
import java.util.Map;

import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Utils;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.task.AddFcmTokenTask;
import io.pijun.george.api.task.DeleteFcmTokenTask;
import io.pijun.george.api.task.DeleteMessageTask;
import io.pijun.george.api.task.DropPackageTask;
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
            L.i("|  we're not logged in. clearing the queue.");
            // not logged in, so empty the queue and get out of here
            while (mQueue.size() > 0) {
                mQueue.remove();
            }
            return;
        }

        while (mQueue.size() > 0) {
            OscarTask task = mQueue.peek();
            OscarAPI api = OscarClient.newInstance(task.accessToken);
            Call call;
            switch (task.apiMethod) {
                case AddFcmTokenTask.NAME:
                    AddFcmTokenTask aftt = (AddFcmTokenTask) task;
                    call = api.addFcmToken(aftt.body);
                    break;
                case DeleteFcmTokenTask.NAME:
                    DeleteFcmTokenTask dftt = (DeleteFcmTokenTask) task;
                    call = api.deleteFcmToken(dftt.fcmToken);
                    break;
                case DeleteMessageTask.NAME:
                    DeleteMessageTask dmt = (DeleteMessageTask) task;
                    call = api.deleteMessage(dmt.messageId);
                    break;
                case DropPackageTask.NAME:
                    DropPackageTask dpt = (DropPackageTask) task;
                    call = api.dropPackage(dpt.hexBoxId, dpt.pkg);
                    break;
                case SendMessageTask.NAME:
                    SendMessageTask smt = (SendMessageTask) task;
                    Map<String, Object> map = Utils.map("cipher_text", smt.message.cipherText, "nonce", smt.message.nonce, "urgent", smt.urgent);
                    call = api.sendMessage(smt.hexUserId, map);
                    break;
                default:
                    throw new RuntimeException("Unknown task type");
            }
            try {
                Response response = call.execute();
                if (response.isSuccessful()) {
                    mQueue.remove();
                } else {
                    OscarError err = OscarError.fromResponse(response);
                    L.i("problem executing task: " + call.request().method() + " " + call.request().url());
                    L.i("|  " + err);
                }
            } catch (IOException e) {
                L.w("task exception", e);
                // a connection problem, so schedule this to continue when the network is back
                JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.schedule(OscarJobService.getJobInfo(this));
                return;
            }
        }
    }

}
