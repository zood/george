package io.pijun.george.service;

import android.app.IntentService;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import io.pijun.george.L;
import io.pijun.george.MessageUtils;
import io.pijun.george.Prefs;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.task.MessageConverter;
import io.pijun.george.api.task.PersistentQueue;

public class MessageQueueService extends IntentService {

    public static Intent newIntent(Context context) {
        return new Intent(context, MessageQueueService.class);
    }

    private static volatile PersistentQueue<Message> sQueue;
    private static final String QUEUE_FILENAME = "message.queue";

    public static PersistentQueue<Message> getQueue(Context context) {
        if (sQueue == null) {
            synchronized (MessageUtils.class) {
                if (sQueue == null) {
                    sQueue = new PersistentQueue<>(context, QUEUE_FILENAME, new MessageConverter());
                }
            }
        }

        return sQueue;
    }

    @WorkerThread
    public static void queueMessage(@NonNull Context context, @NonNull Message msg) {
        getQueue(context).offer(msg);
        context.startService(MessageQueueService.newIntent(context));
    }

    public MessageQueueService() {
        super(MessageQueueService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        L.i("MessageQueueService.onHandleIntent");
        PersistentQueue<Message> queue = getQueue(this);

        Message msg;
        while ((msg = queue.peek()) != null) {
            int result = MessageUtils.unwrapAndProcess(this, msg.senderId, msg.cipherText, msg.nonce);
            switch (result) {
                case MessageUtils.ERROR_NONE:
                    queue.poll();
                    // delete the message from the server
                    String token = Prefs.get(this).getAccessToken();
                    if (!TextUtils.isEmpty(token)) {
                        OscarClient.queueDeleteMessage(this, token, msg.id);
                    }
                    break;
                case MessageUtils.ERROR_NO_NETWORK:
                case MessageUtils.ERROR_REMOTE_INTERNAL:
                    L.w("reschedulable message processing error");
                    rescheduleService();
                    break;
                case MessageUtils.ERROR_INVALID_SENDER_ID:
                case MessageUtils.ERROR_MISSING_CIPHER_TEXT:
                case MessageUtils.ERROR_MISSING_NONCE:
                case MessageUtils.ERROR_INVALID_COMMUNICATION:
                    // just remove the invalid message
                    queue.poll();
                    break;
                default:
                    L.w("error processing message: " + result);
                    break;
            }
        }
    }

    private void rescheduleService() {
        L.i("MQS.rescheduleService");
        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(MessageQueueJobService.getJobInfo(this));
    }

}
