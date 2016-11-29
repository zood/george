package io.pijun.george.service;

import android.app.IntentService;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.google.firebase.crash.FirebaseCrash;
import com.squareup.tape.FileObjectQueue;

import java.io.File;
import java.io.IOException;

import io.pijun.george.L;
import io.pijun.george.MessageUtils;
import io.pijun.george.Prefs;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.task.MessageConverter;

public class MessageQueueService extends IntentService {

    public static Intent newIntent(Context context) {
        return new Intent(context, MessageQueueService.class);
    }

    private static volatile FileObjectQueue<Message> sQueue;
    private static final String QUEUE_FILENAME = "message.queue";

    public static FileObjectQueue<Message> getQueue(Context context) {
        if (sQueue == null) {
            synchronized (MessageUtils.class) {
                if (sQueue == null) {
                    File queueFile = new File(context.getFilesDir(), QUEUE_FILENAME);
                    try {
                        sQueue = new FileObjectQueue<>(queueFile, new MessageConverter());
                    } catch (IOException ex) {
                        // out of disk space?
                        FirebaseCrash.report(ex);
                    }
                }
            }
        }

        return sQueue;
    }

    @WorkerThread
    public static void queueMessage(@NonNull Context context, @NonNull Message msg) {
        getQueue(context).add(msg);
        context.startService(MessageQueueService.newIntent(context));
    }

    public MessageQueueService() {
        super(MessageQueueService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        L.i("MessageQueueService.onHandleIntent");
        FileObjectQueue<Message> mQueue = getQueue(this);

        while (mQueue.size() > 0) {
            Message msg = mQueue.peek();
            int result = MessageUtils.unwrapAndProcess(this, msg.senderId, msg.cipherText, msg.nonce);
            switch (result) {
                case MessageUtils.ERROR_NONE:
                    mQueue.remove();
                    // delete the message from the server
                    String token = Prefs.get(this).getAccessToken();
                    if (!TextUtils.isEmpty(token)) {
                        OscarClient.queueDeleteMessage(this, token, msg.id);
                    }
                    break;
                case MessageUtils.ERROR_NO_NETWORK:
                    L.w("network needed to process message. rescheduling for later.");
                    rescheduleService();
                    break;
                default:
                    L.w("error processing message: " + result);
                    break;
            }
        }
    }

    private void rescheduleService() {
        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(MessageQueueJobService.getJobInfo(this));
    }

}
