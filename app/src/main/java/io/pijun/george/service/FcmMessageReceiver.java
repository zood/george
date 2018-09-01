package io.pijun.george.service;

import android.text.TextUtils;
import android.util.Base64;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;
import java.util.Map;

import androidx.annotation.WorkerThread;
import io.pijun.george.AuthenticationManager;
import io.pijun.george.L;
import io.pijun.george.MessageProcessor;
import io.pijun.george.Prefs;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import retrofit2.Response;

public class FcmMessageReceiver extends FirebaseMessagingService {

    private static final String TYPE_MESSAGE_RECEIVED = "message_received";
    private static final String TYPE_MESSAGE_SYNC_NEEDED = "message_sync_needed";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        L.i("FcmMessageReceiver.onMessageReceived");
        if (!AuthenticationManager.isLoggedIn(this)) {
            L.i("\tnot logged in. returning early.");
            return;
        }
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

    @Override
    public void onNewToken(String fcmToken) {
        L.i("FcmMessageReceiver.onNewToken");

        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (accessToken == null) {
            return;
        }

        // Delete whatever token we have right now (if we have one)
        String oldToken = Prefs.get(this).getFcmToken();
        if (oldToken != null) {
            OscarClient.queueDeleteFcmToken(this, accessToken, oldToken);
        }

        // Is there an actual token? If not, just wipe what we have and return
        if (fcmToken == null) {
            prefs.setFcmToken(null);
            return;
        }

        OscarClient.queueAddFcmToken(this, accessToken, fcmToken);
        prefs.setFcmToken(fcmToken);
    }

    @WorkerThread
    private void handleMessageReceived(Map<String, String> data) {
        L.i("FcmMessageReceiver.handleMessageReceived");
        Message msg = new Message();
        try {
            String msgId = data.get("id");
            if (msgId != null) {
                msg.id = Long.parseLong(msgId);
            }
        } catch (NumberFormatException ignore) {}
        msg.cipherText = Base64.decode(data.get("cipher_text"), Base64.NO_WRAP);
        msg.nonce = Base64.decode(data.get("nonce"), Base64.NO_WRAP);
        msg.senderId = Base64.decode(data.get("sender_id"), Base64.NO_WRAP);

        MessageProcessor.Result result = MessageProcessor.decryptAndProcess(this, msg.senderId, msg.cipherText, msg.nonce);
        if (result == MessageProcessor.Result.Success) {
            String accessToken = Prefs.get(this).getAccessToken();
            // If this isn't a transient message, and we have an access token, delete the message
            if (msg.id != 0 && accessToken != null) {
                OscarAPI api = OscarClient.newInstance(accessToken);
                try {
                    api.deleteMessage(msg.id).execute();
                } catch (IOException ignore) {
                    // No big deal. It will get handled next time the app is opened, and this message
                    // is placed in the processing queue.
                }
            }
        }
    }

    @WorkerThread
    private void handleMesssageSyncNeeded(Map<String, String> data) {
        L.i("handleMessageSyncNeeded");
        long msgId;
        try {
            String idStr = data.get("message_id");
            if (idStr == null) {
                return;
            }
            msgId = Long.parseLong(idStr);
        } catch (NumberFormatException ex) {
            L.w("Error parsing " + data.get("message_id"), ex);
            return;
        }
        String token = Prefs.get(this).getAccessToken();
        if (token == null) {
            return;
        }
        OscarAPI api = OscarClient.newInstance(token);
        try {
            Response<Message> response = api.getMessage(msgId).execute();
            if (response.isSuccessful()) {
                Message msg = response.body();
                if (msg == null) {
                    L.w("unable to decode message from body");
                    return;
                }
                MessageProcessor.Result result = MessageProcessor.decryptAndProcess(getApplicationContext(), msg.senderId, msg.cipherText, msg.nonce);
                if (result == MessageProcessor.Result.Success) {
                    api.deleteMessage(msgId).execute();
                }
            }
        } catch (IOException ex) {
            // network error. Oh, well. We'll get it later.
        }
    }
}
