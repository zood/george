package io.pijun.george.service;

import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Base64;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import io.pijun.george.L;
import io.pijun.george.MessageProcessor;
import io.pijun.george.api.Message;

public class FcmMessageReceiver extends FirebaseMessagingService {

    private static final String TYPE_MESSAGE_RECEIVED = "message_received";
    private static final String TYPE_MESSAGE_SYNC_NEEDED = "message_sync_needed";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        L.i("FcmMessageReceiver.onMessageReceived");
        Map<String, String> data = remoteMessage.getData();
        if (data == null) {
            return;
        }

        String type = data.get("type");
        if (TextUtils.isEmpty(type)) {
            return;
        }
        try {
            switch (type) {
                case TYPE_MESSAGE_RECEIVED:
                    handleMessageReceived(data);
                    break;
                case TYPE_MESSAGE_SYNC_NEEDED:
                    handleMesssageSyncNeeded(data);
                    break;
            }
        } catch (Throwable t) {
            L.w("bad stuff while processing remote message", t);
        }
    }

    @WorkerThread
    private void handleMessageReceived(Map<String, String> data) {
        Message msg = new Message();
        msg.id = Long.parseLong(data.get("id"));
        msg.cipherText = Base64.decode(data.get("cipher_text"), Base64.NO_WRAP);
        msg.nonce = Base64.decode(data.get("nonce"), Base64.NO_WRAP);
        msg.senderId = Base64.decode(data.get("sender_id"), Base64.NO_WRAP);

        MessageProcessor.get().queue(msg);
    }

    @WorkerThread
    private void handleMesssageSyncNeeded(Map<String, String> data) {
        long msgId = Long.parseLong(data.get("message_id"));
        // TODO
    }
}
