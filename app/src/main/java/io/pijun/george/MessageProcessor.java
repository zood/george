package io.pijun.george;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.google.firebase.crash.FirebaseCrash;

import io.pijun.george.api.Message;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.task.MessageConverter;
import io.pijun.george.models.UserRecord;

public class MessageProcessor {

    private static final String QUEUE_FILENAME = "message.queue";
    private static volatile MessageProcessor sSingleton;
    private final PersistentQueue<Message> mQueue;

    private MessageProcessor() {
        mQueue = new PersistentQueue<>(App.getApp(), QUEUE_FILENAME, new MessageConverter());
    }

    @NonNull
    @AnyThread
    public static MessageProcessor get() {
        if (sSingleton == null) {
            synchronized (MessageProcessor.class) {
                if (sSingleton == null) {
                    sSingleton = new MessageProcessor();
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            sSingleton.processQueue();
                        }
                    });
                }
            }
        }
        return sSingleton;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @WorkerThread
    private void processQueue() {
        while (true) {
            Message msg;
            try {
                msg = mQueue.blockingPeek();
                int result = MessageUtils.unwrapAndProcess(App.getApp(), msg.senderId, msg.cipherText, msg.nonce);
                switch (result) {
                    case MessageUtils.ERROR_NONE:
                        mQueue.poll();
                        // delete the message from the server
                        String token = Prefs.get(App.getApp()).getAccessToken();
                        if (!TextUtils.isEmpty(token)) {
                            OscarClient.queueDeleteMessage(App.getApp(), token, msg.id);
                        }
                        break;
                    case MessageUtils.ERROR_NO_NETWORK:
                    case MessageUtils.ERROR_REMOTE_INTERNAL:
                        L.w("reschedulable message processing error");
                        // sleep for 60 seconds, then try again
                        Thread.sleep(60 * DateUtils.SECOND_IN_MILLIS);
                        break;
                    case MessageUtils.ERROR_DECRYPTION_FAILED:
                        // somebody must have a corrupt keypair
                    case MessageUtils.ERROR_INVALID_SENDER_ID:
                    case MessageUtils.ERROR_MISSING_CIPHER_TEXT:
                    case MessageUtils.ERROR_MISSING_NONCE:
                    case MessageUtils.ERROR_INVALID_COMMUNICATION:
                        // just remove the invalid message
                    case MessageUtils.ERROR_NOT_LOGGED_IN:
                        // if we're not logged in, toss the message
                    case MessageUtils.ERROR_DATABASE_EXCEPTION:
                    case MessageUtils.ERROR_DATABASE_INCONSISTENCY:
                    case MessageUtils.ERROR_ENCRYPTION_FAILED:
                    case MessageUtils.ERROR_NOT_A_FRIEND:
                    case MessageUtils.ERROR_UNKNOWN_SENDER:
                    case MessageUtils.ERROR_UNKNOWN:
                    default:
                        mQueue.poll();
                        L.w("error processing message: " + result);
                        UserRecord user = DB.get(App.getApp()).getUser(msg.senderId);
                        if (user != null) {
                            L.w("\tfrom " + user.username);
                        } else {
                            L.w("\tfrom an unknown user");
                        }
                        break;
                }
            } catch (Throwable t) {
                L.w("MessageProcessor.processQueue exception", t);
                FirebaseCrash.report(t);
            }
        }
    }

    @AnyThread
    public void queue(@NonNull Message msg) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                mQueue.offer(msg);
            }
        });
    }

}
