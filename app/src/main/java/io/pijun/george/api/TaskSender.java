package io.pijun.george.api;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.format.DateUtils;

import java.io.IOException;

import io.pijun.george.CloudLogger;
import io.pijun.george.L;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.task.AddFcmTokenTask;
import io.pijun.george.api.task.DeleteFcmTokenTask;
import io.pijun.george.api.task.DeleteMessageTask;
import io.pijun.george.api.task.DropMultiplePackagesTask;
import io.pijun.george.api.task.DropPackageTask;
import io.pijun.george.api.task.OscarTask;
import io.pijun.george.api.task.SendMessageTask;
import io.pijun.george.queue.PersistentQueue;
import retrofit2.Call;
import retrofit2.Response;

public final class TaskSender {

    private volatile static TaskSender sSingleton;
    private final Handler mHandler;
    private volatile boolean isRunning = false;

    private TaskSender() {
        HandlerThread thread = new HandlerThread(TaskSender.class.getSimpleName());
        thread.start();
        mHandler = new Handler(thread.getLooper());
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
    private void processQueue(@NonNull Context ctx) {
        PersistentQueue<OscarTask> queue = OscarClient.getQueue(ctx);
        PowerManager pwrMgr = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pwrMgr == null) {
            CloudLogger.log("PowerManager is null");
            return;
        }

        OscarTask task;
        //noinspection InfiniteLoopStatement
        while (true) {
            task = queue.blockingPeek();

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
                case DropMultiplePackagesTask.NAME:
                    DropMultiplePackagesTask dmpt = (DropMultiplePackagesTask) task;
                    call = api.dropMultiplePackages(dmpt.packages);
                    break;
                case DropPackageTask.NAME:
                    DropPackageTask dpt = (DropPackageTask) task;
                    call = api.dropPackage(dpt.hexBoxId, dpt.pkg);
                    break;
                case SendMessageTask.NAME:
                    SendMessageTask smt = (SendMessageTask) task;
                    OutboundMessage om = new OutboundMessage();
                    om.cipherText = smt.message.cipherText;
                    om.nonce = smt.message.nonce;
                    om.urgent = smt.urgent;
                    om.isTransient = smt.isTransient;
                    call = api.sendMessage(smt.hexUserId, om);
                    break;
                default:
                    throw new RuntimeException("Unknown task type");
            }
            PowerManager.WakeLock wakeLock = pwrMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskSenderLock");
            try {
                wakeLock.acquire(30 * DateUtils.SECOND_IN_MILLIS);
                Response response = call.execute();
                if (response.isSuccessful()) {
                    queue.poll();
                } else {
                    OscarError err = OscarError.fromResponse(response);
                    L.i("problem executing task: " + call.request().method() + " " + call.request().url());
                    if (err != null) {
                        switch (err.code) {
                            case OscarError.ERROR_INVALID_ACCESS_TOKEN:
                                // toss the message. This was probably caused by a bug elsewhere in the app
                                queue.poll();
                                break;
                            default:
                        }
                    }

                    L.i("  " + err);
                }
            } catch (IOException e) {
                L.w("task (" + task.apiMethod + ") exception", e);
                // a connection problem. we'll try again when the connection is back.
                sleep(60 * DateUtils.SECOND_IN_MILLIS);
                //noinspection UnnecessaryContinue
                continue;
            } catch (Throwable t) {
                CloudLogger.log(t);
                queue.poll();
            } finally {
                try {
                    // There's a chance that this wakelock may timeout right before we release it
                    // If that happens, calling release will throw an Exception.
                    wakeLock.release();
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * A wrapped up version of Thread.sleep() that converts the InterruptedException into a RuntimeException
     * so we don't have try-catches littering our code everytime we want to sleep.
     *
     * Also, we never interrupt threads in Pijun, so the RuntimeException should never really occur.
     * @param millis Duration to sleep in milliseconds
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted while sleeping", ie);
        }
    }

    @AnyThread
    synchronized public void start(@NonNull Application ctx) {
        if (isRunning) {
            return;
        }
        isRunning = true;
        mHandler.post(new WorkerRunnable() {
            @Override
            public void run() {
                processQueue(ctx);
            }
        });
    }
}
