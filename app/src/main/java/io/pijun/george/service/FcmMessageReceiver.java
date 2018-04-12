package io.pijun.george.service;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;
import java.util.Map;

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
        PowerManager pwrMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pwrMgr == null) {
            return;
        }
        PowerManager.WakeLock lock = pwrMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FcmMessageReceiverLock");
        lock.acquire(5 * DateUtils.SECOND_IN_MILLIS);
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
        L.i("FcmMessageReceiver.handleMessageReceived");
        Message msg = new Message();
        msg.id = Long.parseLong(data.get("id"));
        msg.cipherText = Base64.decode(data.get("cipher_text"), Base64.NO_WRAP);
        msg.nonce = Base64.decode(data.get("nonce"), Base64.NO_WRAP);
        msg.senderId = Base64.decode(data.get("sender_id"), Base64.NO_WRAP);

        MessageProcessor.get().queue(msg);
    }

    @WorkerThread
    private void handleMesssageSyncNeeded(Map<String, String> data) {
        L.i("handleMessageSyncNeeded");
        long msgId;
        try {
            msgId = Long.parseLong(data.get("message_id"));
            L.i("message id " + msgId);
        } catch (NumberFormatException ex) {
            L.w("Error parsing " + data.get("message_id"), ex);
            return;
        }
        String token = Prefs.get(this).getAccessToken();
        if (TextUtils.isEmpty(token)) {
            return;
        }
        OscarAPI client = OscarClient.newInstance(token);
        try {
            Response<Message> response = client.getMessage(msgId).execute();
            if (response.isSuccessful()) {
                Message msg = response.body();
                if (msg == null) {
                    FirebaseCrash.logcat(Log.ERROR, L.TAG, "unable to decode message from body");
                    return;
                }
                MessageProcessor.get().queue(msg);
            }
        } catch (IOException ex) {
            // network error. Oh, well. We'll get it later.
        }
    }
}
