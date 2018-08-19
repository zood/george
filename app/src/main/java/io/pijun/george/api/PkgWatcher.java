package io.pijun.george.api;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.pijun.george.App;
import io.pijun.george.Config;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.crypto.EncryptedData;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class PkgWatcher {

    private static int count = 1;
    private Handler handler;
    @NonNull final private Listener listener;
    private WebSocket socket;

    public PkgWatcher(@NonNull Listener listener) {
        this.listener = listener;
    }

    @AnyThread
    public void connect(@NonNull final String accessToken) {
        HandlerThread thread = new HandlerThread(String.format(Locale.US, "PackageWatcher-%d", count++));
        thread.start();
        handler = new Handler(thread.getLooper());

        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
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
                    clientBuilder.connectTimeout(10, TimeUnit.SECONDS);
                    clientBuilder.readTimeout(10, TimeUnit.SECONDS);
                    clientBuilder.writeTimeout(10, TimeUnit.SECONDS);
                    clientBuilder.pingInterval(10, TimeUnit.SECONDS);
                    OkHttpClient client = clientBuilder.build();

                    String url = "wss://" + Config.apiAddress() + "/alpha/drop-boxes/watch";
                    Request req = new Request.
                            Builder().
                            url(url).
                            build();

                    socket = client.newWebSocket(req, new OscarSocketListener());
                } catch (Throwable t) {
                    L.w("Exception trying to connect package watcher", t);
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            listener.onDisconnected(4000, "No network available");
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

    public void watch(@NonNull byte[] boxId) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                WebSocket s = socket;
                if (s != null) {
                    byte[] msg = new byte[boxId.length + 1];
                    msg[0] = 1;
                    System.arraycopy(boxId, 0, msg, 1, boxId.length);
                    ByteString bytes = ByteString.of(msg);
                    s.send(bytes);
                }
            }
        });
    }

    private class OscarSocketListener extends WebSocketListener {

        @Override
        @WorkerThread
        public void onOpen(WebSocket webSocket, Response response) {
//            L.i("OSL.onOpen");
        }

        @Override
        @WorkerThread
        public void onMessage(WebSocket webSocket, ByteString byteString) {
//            L.i("OSL.onMessage");
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
                System.arraycopy(binary, 1, boxId, 0, Constants.DROP_BOX_ID_LENGTH);

                int msgOffset = 1 + Constants.DROP_BOX_ID_LENGTH;
                ByteArrayInputStream bais = new ByteArrayInputStream(binary, msgOffset, binary.length - msgOffset);
                InputStreamReader isr = new InputStreamReader(bais);
                EncryptedData encMsg = OscarClient.sGson.fromJson(isr, EncryptedData.class);

                listener.onMessageReceived(boxId, encMsg);
            } catch (Throwable t) {
                L.w("PackageWatcher exception", t);
            }
        }

        @Override
        @WorkerThread
        public void onClosed(WebSocket webSocket, int code, String reason) {
//            L.i("OSL.onClosed");
            listener.onDisconnected(code, reason);
            handler.post(new WorkerRunnable() {
                @Override
                public void run() {
                    socket = null;
                }
            });
            handler.getLooper().quitSafely();
        }

        @Override
        @WorkerThread
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
//            L.w("OSL.onFailure");
            handler.post(new WorkerRunnable() {
                @Override
                public void run() {
                    socket = null;
                }
            });
            handler.getLooper().quitSafely();
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    listener.onDisconnected(4001, t.getLocalizedMessage());
                }
            });

        }
    }

    public interface Listener {
        @WorkerThread
        void onDisconnected(int code, String reason);
        @WorkerThread
        void onMessageReceived(@NonNull byte[] boxId, @NonNull EncryptedData data);
    }

}
