package io.pijun.george.api;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import io.pijun.george.App;
import io.pijun.george.Config;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.crypto.EncryptedData;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class OscarSocket {

    @SuppressWarnings("unused")
    private static final byte SocketCmdNop = 0;
    private static final byte SocketCmdWatch = 1;
    @SuppressWarnings("unused")
    private static final byte SocketCmdIgnore = 2;

    private static final byte SocketMsgPackage = 1;
    private static final byte SocketMsgPushNotification = 2;

    @NonNull final private Listener listener;
    private static final Handler handler;
    private WebSocket socket;

    static {
        HandlerThread thread = new HandlerThread("OscarSocket");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public OscarSocket(@NonNull Listener listener) {
        this.listener = listener;
    }

    @AnyThread
    public void connect(@NonNull final String accessToken) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
                    ConnectionSpec connSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .tlsVersions(TlsVersion.TLS_1_2)
                            .cipherSuites(
                                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
                            .build();
                    clientBuilder.connectionSpecs(Collections.singletonList(connSpec));
                    clientBuilder.addInterceptor(new Interceptor() {
                        @Override @NonNull
                        public Response intercept(@NonNull Chain chain) throws IOException {
                            Request request = chain.request();
                            request = request.newBuilder().addHeader("Sec-Websocket-Protocol", accessToken).build();
                            return chain.proceed(request);
                        }
                    });
                    clientBuilder.connectTimeout(10, TimeUnit.SECONDS);
                    clientBuilder.readTimeout(10, TimeUnit.SECONDS);
                    clientBuilder.writeTimeout(10, TimeUnit.SECONDS);
                    clientBuilder.pingInterval(10, TimeUnit.SECONDS);
                    OkHttpClient client = clientBuilder.build();

                    String url = Config.wsScheme() + "://" + Config.apiAddress() + "/1/sockets";
                    Request req = new Request.
                            Builder().
                            url(url).
                            build();

                    socket = client.newWebSocket(req, new InternalSocketListener());
                } catch (Throwable t) {
                    L.w("Exception trying to connect oscar socket", t);
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            listener.onDisconnect(4000, "No network available");
                        }
                    });
                }
            }
        });
    }

    @AnyThread
    public void disconnect() {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                if (socket != null) {
                    socket.close(1001, "Finished listening");
                }
            }
        });
    }

    @AnyThread
    public void watch(@NonNull byte[] boxId) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                WebSocket s = socket;
                if (s != null) {
                    byte[] msg = new byte[boxId.length + 1];
                    msg[0] = SocketCmdWatch;
                    System.arraycopy(boxId, 0, msg, 1, boxId.length);
                    ByteString bytes = ByteString.of(msg);
                    s.send(bytes);
                }
            }
        });
    }

    //region Message handlers

    private void handleDroppedPackage(@NonNull byte[] bytes) {
        // Message prefix + drop box id + json (at least opening and closing brackets)
        int minSize = 1 + Constants.DROP_BOX_ID_LENGTH + 2;
        if (bytes.length < minSize) {
            L.w("OS.handleDroppedPackage found an unreasonably short message: " + bytes.length);
            return;
        }

        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        System.arraycopy(bytes, 1, boxId, 0, Constants.DROP_BOX_ID_LENGTH);

        int pkgOffset = 1 + Constants.DROP_BOX_ID_LENGTH;
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes, pkgOffset, bytes.length - pkgOffset);
        InputStreamReader isr = new InputStreamReader(bais);
        EncryptedData pkg = OscarClient.sGson.fromJson(isr, EncryptedData.class);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                listener.onPackageReceived(boxId, pkg);
            }
        });
    }

    private void handlePushNotification(byte[] bytes) {
        L.i("OS.handlePushNotification");
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 1, bytes.length-1);
        InputStreamReader isr = new InputStreamReader(bais);
        PushNotification pn = OscarClient.sGson.fromJson(isr, PushNotification.class);
        if (!pn.isValid()) {
            L.i("OS.handlePushNotification received an invalid notification");
            return;
        }

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                listener.onPushNotificationReceived(pn);
            }
        });
    }

    //endregion

    //region Internal websocket listener

    private class InternalSocketListener extends WebSocketListener {

        @Override
        @WorkerThread
        public void onClosed(WebSocket webSocket, int code, String reason) {
            handler.post(new WorkerRunnable() {
                @Override
                public void run() {
                    socket = null;
                }
            });
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    listener.onDisconnect(code, reason);
                }
            });
        }

        @Override
        @WorkerThread
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            L.w("ISL.onFailure");
            handler.post(new WorkerRunnable() {
                @Override
                public void run() {
                    socket = null;
                }
            });
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    listener.onDisconnect(4001, t.getLocalizedMessage());
                }
            });
        }

        @Override
        @WorkerThread
        public void onMessage(WebSocket webSocket, ByteString byteString) {
//            L.i("ISL.onMessage");
            if (byteString == null || byteString.size() < 3) {
                L.i("ISL received a null (or trivially small) message");
                return;
            }
            byte[] bytes = byteString.toByteArray();
            try {
                if (bytes[0] == SocketMsgPackage) {
                    handleDroppedPackage(bytes);
                } else if (bytes[0] == SocketMsgPushNotification) {
                    handlePushNotification(bytes);
                } else {
                    L.w("Unknown message type received on oscar socket: " + bytes[0]);
                }
            } catch (Throwable t) {
                L.w("OscarSocket.ISL exception", t);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    listener.onConnect();
                }
            });
        }
    }

    //endregion

    //region Listener interface

    public interface Listener {
        @WorkerThread
        void onConnect();
        @WorkerThread
        void onDisconnect(int code, String reason);
        @WorkerThread
        void onPackageReceived(@NonNull byte[] boxId, @NonNull EncryptedData data);
        @WorkerThread
        void onPushNotificationReceived(@NonNull PushNotification notification);
    }

    //endregion

}
