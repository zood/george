package io.pijun.george.api;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import io.pijun.george.App;
import io.pijun.george.Config;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.MessageProcessor;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class PackageWatcher {

    private WebSocket mSocket;
    private OkHttpClient mClient;
    private Context mContext;

    private PackageWatcher(@NonNull Context c) {
        mContext = c.getApplicationContext();
    }

    @WorkerThread
    public static PackageWatcher createWatcher(@NonNull Context context, @NonNull String accessToken) {
        PackageWatcher watcher = new PackageWatcher(context);
        String url = "wss://" + Config.apiAddress() + "/alpha/drop-boxes/watch";
        try {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
            clientBuilder.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(@NonNull Chain chain) throws IOException {
                    Request request = chain.request();
                    request = request.newBuilder().addHeader("X-Oscar-Access-Token", accessToken).build();
                    return chain.proceed(request);
                }
            });
            clientBuilder.connectTimeout(15, TimeUnit.SECONDS);
            watcher.mClient = clientBuilder.build();
            Request req = new Request.
                    Builder().
                    url(url).
                    build();
            watcher.mSocket = watcher.mClient.newWebSocket(req, watcher.mSocketListener);
        } catch (Throwable t) {
            L.w("Exception trying to connect package watcher", t);
            return null;
        }

        return watcher;
    }

    @AnyThread
    public void disconnect() {
        mSocket.close(1001, "Finished listening");
    }

    public void watch(@NonNull byte[] boxId) {
        byte[] msg = new byte[boxId.length + 1];
        msg[0] = 1;
        System.arraycopy(boxId, 0, msg, 1, boxId.length);

        ByteString bytes = ByteString.of(msg);
        mSocket.send(bytes);
    }

    private WebSocketListener mSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
        }

        @Override
        @AnyThread
        public void onMessage(WebSocket webSocket, ByteString byteString) {
            if (byteString == null || byteString.size() == 0) {
                L.i("  received a null/empty binary message");
                return;
            }
            byte[] binary = byteString.toByteArray();
            try {
                // quick sanity check
                // message is command (1 byte) + box id + msg (at least 2 bytes)
                int minLength = 1 + Constants.DROP_BOX_ID_LENGTH + 1;
                if (binary.length <= minLength) {
                    L.w("  received an invalid message from the server. length was only " + binary.length);
                    return;
                }

                // check for the opening byte
                if (binary[0] != 1) {
                    L.w("  PackageWatcher received incorrect opening byte: " + binary[0]);
                    return;
                }

                byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
//                L.i("PW.boxId " + Hex.toHexString(boxId));
                System.arraycopy(binary, 1, boxId, 0, Constants.DROP_BOX_ID_LENGTH);

                int msgOffset = 1 + Constants.DROP_BOX_ID_LENGTH;
                ByteArrayInputStream bais = new ByteArrayInputStream(binary, msgOffset, binary.length - msgOffset);
                InputStreamReader isr = new InputStreamReader(bais);
                EncryptedData encMsg = OscarClient.sGson.fromJson(isr, EncryptedData.class);
                App.runInBackground(new WorkerRunnable() {
                    @Override
                    public void run() {
                        FriendRecord friend = DB.get().getFriendByReceivingBoxId(boxId);
                        if (friend == null) {
                            L.i("  can't find user associated with receiving box id");
                            return;
                        }
                        MessageProcessor.Result result = MessageProcessor.decryptAndProcess(mContext, friend.user.userId, encMsg.cipherText, encMsg.nonce);
                        if (result != MessageProcessor.Result.Success) {
                            L.i("  error decrypting+processing dropped message: " + result);
                        }
                    }
                });
            } catch (Throwable t) {
                L.w("PackageWatcher exception", t);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            // don't need to worry about handling disconnects for now (or ever?)
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            L.w("PackageWatcher.onFailure", t);
        }
    };
}
