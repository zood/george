package io.pijun.george.api;

import android.app.Application;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.google.firebase.crash.FirebaseCrash;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Utils;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.task.AddFcmTokenTask;
import io.pijun.george.api.task.DeleteFcmTokenTask;
import io.pijun.george.api.task.DeleteMessageTask;
import io.pijun.george.api.task.DropPackageTask;
import io.pijun.george.api.task.OscarTask;
import io.pijun.george.PersistentQueue;
import io.pijun.george.api.task.SendMessageTask;
import io.pijun.george.service.OscarJobService;
import retrofit2.Call;
import retrofit2.Response;

public class TaskSender {

    private volatile static TaskSender sSingleton;
    private final Handler mHandler;
    private CopyOnWriteArrayList<WeakReference<OneTimeListener>> mListeners = new CopyOnWriteArrayList<>();

    private TaskSender() {
        HandlerThread thread = new HandlerThread(TaskSender.class.getSimpleName());
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }


    public TaskSender addListener(@NonNull OneTimeListener l) {
        mListeners.add(new WeakReference<>(l));
        return this;
    }

    @AnyThread
    @NonNull
    public static TaskSender get() {
        if (sSingleton == null) {
            synchronized (TaskSender.class) {
                if (sSingleton == null) {
                    sSingleton = new TaskSender();
                }
            }
        }
        return sSingleton;
    }

    @WorkerThread
    private void notifyListeners() {
        for (WeakReference<OneTimeListener> l : mListeners) {
            OneTimeListener listener = l.get();
            if (listener != null) {
                listener.taskSenderPaused();
            }
        }
        mListeners.clear();
    }

    @WorkerThread
    private void processQueue(@NonNull Context ctx) {
        PersistentQueue<OscarTask> queue = OscarClient.getQueue(ctx);
        Prefs prefs = Prefs.get(ctx);
        PowerManager pwrMgr = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pwrMgr == null) {
            FirebaseCrash.log("PowerManager is null");
            return;
        }

        OscarTask task;
        while (true) {
            try {
                task = queue.blockingPeek();
            } catch (InterruptedException ex) {
                FirebaseCrash.report(ex);
                L.w("Unable to take task", ex);
                return;
            }

            // make sure we're still logged in
            if (TextUtils.isEmpty(prefs.getAccessToken())) {
                L.i("OscarTasksService.onHandleIntent - we're not logged in. Clearing the queue.");
                // not logged in, so empty the queue and get out of here
                queue.clear();
                return;
            }

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
            PowerManager.WakeLock wakeLock = pwrMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskSenderLock");
            try {
                wakeLock.acquire(30 * DateUtils.SECOND_IN_MILLIS);
                Response response = call.execute();
                if (response.isSuccessful()) {
                    try {
                        queue.take();
                    } catch (InterruptedException ie) {
                        // shouldn't happen
                        FirebaseCrash.report(ie);
                        L.w("Take after successful response interrupted", ie);
                    }
                } else {
                    OscarError err = OscarError.fromResponse(response);
                    L.i("problem executing task: " + call.request().method() + " " + call.request().url());
                    L.i("  " + err);
                }
            } catch (IOException e) {
                L.w("task exception", e);
                // a connection problem. we'll try again when the connection is back.
                return;
            } finally {
                try {
                    // There's a chance that this wakelock may timeout right before we release it
                    // If that happens, calling release will throw an Exception.
                    wakeLock.release();
                } catch (Exception ignore) {
                }
            }

            if (queue.size() == 0) {
                // no more messages, so let our listeners know we're done for now
                notifyListeners();
            }
        }
    }

    @AnyThread
    synchronized public void start(@NonNull Application ctx) {
        mHandler.post(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    processQueue(ctx);
                    notifyListeners();
                    JobScheduler scheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    if (scheduler == null) {
                        FirebaseCrash.log("JobScheduler is null");
                        return;
                    }
                    scheduler.schedule(OscarJobService.getJobInfo(ctx));
                } catch (Throwable t) {
                    FirebaseCrash.report(t);
                    L.w("Exception processing queue", t);
                }
            }
        });
    }

    public interface OneTimeListener {
        void taskSenderPaused();
    }
}
