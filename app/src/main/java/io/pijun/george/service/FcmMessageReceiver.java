package io.pijun.george.service;

import android.support.annotation.WorkerThread;
import android.util.Base64;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import io.pijun.george.L;
import io.pijun.george.MessageUtils;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarClient;

public class FcmMessageReceiver extends FirebaseMessagingService {

    private static final String TYPE_MESSAGE_RECEIVED = "message_received";
    private static final String TYPE_MESSAGE_SYNC_NEEDED = "message_sync_needed";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (data == null) {
            return;
        }

        String type = data.get("type");
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
        L.i("FMR.handleMessageReceived: " + msg);

        int result = MessageUtils.unwrapAndProcess(this, msg.senderId, msg.cipherText, msg.nonce);
        if (result == MessageUtils.ERROR_NONE) {
            // delete the message from the server
//            OscarClient.queueDeleteMessage(this, msg.id);
        } else {
            L.w("error processing message: " + result);
        }
    }

    private void handleMesssageSyncNeeded(Map<String, String> data) {
        long msgId = Long.parseLong(data.get("message_id"));

    }
}
