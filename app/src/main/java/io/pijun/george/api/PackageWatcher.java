package io.pijun.george.api;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import io.pijun.george.Constants;
import io.pijun.george.DB;
import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.MessageUtils;
import io.pijun.george.crypto.PKEncryptedMessage;
import io.pijun.george.models.FriendRecord;

public class PackageWatcher extends WebSocketAdapter {

    private WebSocket mSocket;
    private Context mContext;

    @WorkerThread
    public static PackageWatcher createWatcher(@NonNull Context context, @NonNull String accessToken) {
        PackageWatcher watcher = new PackageWatcher(context);
        try {
            watcher.mSocket = new WebSocketFactory()
                    .createSocket("ws://192.168.1.76:9999/alpha/drop-boxes/watch", 15000)
                    .addListener(watcher)
                    .addHeader("X-Oscar-Access-Token", accessToken)
                    .connect();
        } catch (Throwable t) {
            L.w("Exception trying to connect package watcher", t);
            return null;
        }

        return watcher;
    }

    private PackageWatcher(@NonNull Context c) {
        mContext = c;
    }

    @Override
    public void onDisconnected(WebSocket websocket,
                               WebSocketFrame serverCloseFrame,
                               WebSocketFrame clientCloseFrame,
                               boolean closedByServer) throws Exception {
        // don't need to worry about handling disconnects for now (or ever?)
    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        L.i("PW.onBinaryMessage");
        try {
            if (binary == null || binary.length == 0) {
                L.i("received a null/empty binary message");
                return;
            }

            // quick sanity check
            // message is command (1 byte) + box id + msg (at least 2 bytes)
            int minLength = 1 + Constants.DROP_BOX_ID_LENGTH + 1;
            if (binary.length <= minLength) {
                L.w("received an invalid message from the server. length was only " + binary.length);
                return;
            }

            // check for the opening byte
            if (binary[0] != 1) {
                L.w("PackageWatcher received incorrect opening byte: " + binary[0]);
                return;
            }

            byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
            System.arraycopy(binary, 1, boxId, 0, Constants.DROP_BOX_ID_LENGTH);
            L.i("|  boxId: " + Hex.toHexString(boxId));

            int msgOffset = 1 + Constants.DROP_BOX_ID_LENGTH;
            ByteArrayInputStream bais = new ByteArrayInputStream(binary, msgOffset, binary.length - msgOffset);
            InputStreamReader isr = new InputStreamReader(bais);
            PKEncryptedMessage encMsg = OscarClient.sGson.fromJson(isr, PKEncryptedMessage.class);
            FriendRecord friend = DB.get(mContext).getFriendByReceivingBoxId(boxId);
            if (friend == null) {
                L.i("can't find user associated with receiving box id");
                return;
            }
            L.i("|  friend: " + friend);
            int result = MessageUtils.unwrapAndProcess(mContext, friend.userId, encMsg.cipherText, encMsg.nonce);
            if (result != MessageUtils.ERROR_NONE) {
                L.i("error unwrapping+processing message: " + result);
            }
        } catch (Throwable t) {
            L.w("PackageWatcher exception", t);
        }
    }

    @WorkerThread
    public void disconnect() {
        try {
            mSocket.disconnect();
        } catch (Throwable ignore) {}
    }

    public void watch(byte[] boxId) {
        WebSocketFrame firstFrame = WebSocketFrame.createBinaryFrame(new byte[]{1}).setFin(false);
        WebSocketFrame lastFrame = WebSocketFrame.createContinuationFrame(boxId).setFin(true);
        mSocket.sendFrame(firstFrame).sendFrame(lastFrame);
    }
}
