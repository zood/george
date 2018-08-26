package io.pijun.george.api;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.pijun.george.Config;
import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Sodium;
import io.pijun.george.api.adapter.BytesToBase64Adapter;
import io.pijun.george.api.adapter.CommTypeAdapter;
import io.pijun.george.api.task.AddFcmTokenTask;
import io.pijun.george.api.task.DeleteFcmTokenTask;
import io.pijun.george.api.task.DeleteMessageTask;
import io.pijun.george.api.task.OscarTask;
import io.pijun.george.api.task.QueueConverter;
import io.pijun.george.api.task.SendMessageTask;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.UserRecord;
import io.pijun.george.queue.PersistentQueue;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OscarClient {

    public static final Gson sGson;
    private static volatile PersistentQueue<OscarTask> sQueue;
    private static final String QUEUE_FILENAME = "oscar.queue";
    private static final ConcurrentHashMap<String, WeakReference<OscarAPI>> sApiCache = new ConcurrentHashMap<>();

    static {
        sGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(byte[].class, new BytesToBase64Adapter())
                .registerTypeAdapter(CommType.class, new CommTypeAdapter())
                .create();
    }

    @NonNull
    public static OscarAPI newInstance(@Nullable final String accessToken) {
        // check if we already have an API object for this token around
        // However, if the token is null, just create a new api object, because
        // ConcurrentHashMap doesn't support null keys
        if (accessToken != null) {
            WeakReference<OscarAPI> apiRef = sApiCache.get(accessToken);
            if (apiRef != null) {
                OscarAPI api = apiRef.get();
                if (api != null) {
                    return api;
                }
            }
        }

        String url = "https://" + Config.apiAddress() + "/alpha/";
        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
//        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
//        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
//        httpBuilder.addInterceptor(interceptor);
        if (accessToken != null) {
            httpBuilder.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(@NonNull Chain chain) throws IOException {
                    Request request = chain.request();
                    request = request.newBuilder()
                            .addHeader("X-Oscar-Access-Token", accessToken)
                            .build();

                    return chain.proceed(request);
                }
            });
        }
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create(sGson))
                .client(httpBuilder.build())
                .build();

        // if the access token is not null, store it in our cache
        OscarAPI api = retrofit.create(OscarAPI.class);
        if (accessToken != null) {
            sApiCache.put(accessToken, new WeakReference<>(api));
        }
        return api;
    }

    //region Convenience methods some API methods

    @NonNull
    @CheckResult
    static PersistentQueue<OscarTask> getQueue(@NonNull Context context) {
        if (sQueue == null) {
            synchronized (OscarClient.class) {
                if (sQueue == null) {
                    sQueue = new PersistentQueue<>(context, QUEUE_FILENAME, new QueueConverter());
                }
            }
        }

        return sQueue;
    }

    @WorkerThread
    @CheckResult @Nullable
    public static String immediatelySendMessage(@NonNull UserRecord toUser, @NonNull String accessToken, @NonNull KeyPair keyPair, @NonNull UserComm comm, boolean urgent, boolean isTransient) {
        EncryptedData encMsg = Sodium.publicKeyEncrypt(comm.toJSON(), toUser.publicKey, keyPair.secretKey);
        if (encMsg == null) {
            return "Encrypting msg to " + toUser.username + " failed.";
        }
        OutboundMessage om = new OutboundMessage();
        om.cipherText = encMsg.cipherText;
        om.nonce = encMsg.nonce;
        om.urgent = urgent;
        om.isTransient = isTransient;

        OscarAPI api = newInstance(accessToken);
        try {
            retrofit2.Response<Void> response = api.sendMessage(Hex.toHexString(toUser.userId), om).execute();
            if (response.isSuccessful()) {
                return null;
            }
            OscarError err = OscarError.fromResponse(response);
            if (err != null) {
                return err.toString();
            }
            return "Unknown server error received while sending message";
        } catch (IOException ex) {
            return "Network error: " + ex.getLocalizedMessage();
        }
    }

    @WorkerThread
    public static void queueAddFcmToken(@NonNull Context context, @NonNull String accessToken, @NonNull Map<String, String> body) {
        AddFcmTokenTask aftt = new AddFcmTokenTask(accessToken);
        aftt.body = body;
        getQueue(context).offer(aftt);
    }

    @WorkerThread
    public static void queueDeleteFcmToken(@NonNull Context context, @NonNull String accessToken, @NonNull String fcmToken) {
        DeleteFcmTokenTask dftt = new DeleteFcmTokenTask(accessToken);
        dftt.fcmToken = fcmToken;
        getQueue(context).offer(dftt);
    }

    @WorkerThread
    public static void queueDeleteMessage(@NonNull Context context, @NonNull String accessToken, long msgId) {
        DeleteMessageTask dmt = new DeleteMessageTask(accessToken);
        dmt.messageId = msgId;
        getQueue(context).offer(dmt);
    }

    @WorkerThread @CheckResult
    public static String queueSendMessage(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm, boolean urgent, boolean isTransient) {
        return queueSendMessage(context, user, comm.toJSON(), urgent, isTransient);
    }

    @WorkerThread @CheckResult
    public static String queueSendMessage(@NonNull Context context, @NonNull UserRecord user,
                                        @NonNull byte[] msgBytes, boolean urgent, boolean isTransient) {
        Prefs prefs = Prefs.get(context);
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            String msg = "Oops! Tried sending a message while the keypair was null";
            L.w(msg);
            return msg;
        }
        String token = prefs.getAccessToken();
        if (TextUtils.isEmpty(token)) {
            String msg = "Somebody is trying to send a message when we have no access token";
            L.w(msg);
            return msg;
        }
        EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, user.publicKey, keyPair.secretKey);
        if (encMsg == null) {
            String msg = "Encrypting msg to " + user.username + " failed.";
            L.w(msg);
            return msg;
        }
        SendMessageTask smt = new SendMessageTask(token);
        smt.hexUserId = Hex.toHexString(user.userId);
        smt.message = encMsg;
        smt.urgent = urgent;
        smt.isTransient = isTransient;
        getQueue(context).offer(smt);

        return null;
    }

    //endregion
}
