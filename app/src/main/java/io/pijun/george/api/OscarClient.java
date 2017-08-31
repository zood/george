package io.pijun.george.api;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.Map;

import io.pijun.george.Constants;
import io.pijun.george.Hex;
import io.pijun.george.api.adapter.BytesToBase64Adapter;
import io.pijun.george.api.adapter.CommTypeAdapter;
import io.pijun.george.api.task.AddFcmTokenTask;
import io.pijun.george.api.task.DeleteFcmTokenTask;
import io.pijun.george.api.task.DeleteMessageTask;
import io.pijun.george.api.task.DropPackageTask;
import io.pijun.george.api.task.OscarTask;
import io.pijun.george.api.task.QueueConverter;
import io.pijun.george.api.task.SendMessageTask;
import io.pijun.george.api.task.PersistentQueue;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.service.OscarTasksService;
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

    static {
        sGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(byte[].class, new BytesToBase64Adapter())
                .registerTypeAdapter(CommType.class, new CommTypeAdapter())
                .create();
    }

    public static PersistentQueue<OscarTask> getQueue(Context context) {
        if (sQueue == null) {
            synchronized (OscarClient.class) {
                if (sQueue == null) {
                    sQueue = new PersistentQueue<>(context, QUEUE_FILENAME, new QueueConverter());
                }
            }
        }

        return sQueue;
    }

    public static OscarAPI newInstance(final String accessToken) {
        String url;
        if (Constants.USE_PRODUCTION) {
            url = "https://api.pijun.io/alpha/";
        } else {
            url = "http://192.168.1.76:9999/alpha/";
        }

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

        return retrofit.create(OscarAPI.class);
    }

    @WorkerThread
    public static void queueAddFcmToken(@NonNull Context context, @NonNull String accessToken, @NonNull Map<String, String> body) {
        AddFcmTokenTask aftt = new AddFcmTokenTask(accessToken);
        aftt.body = body;
        getQueue(context).offer(aftt);
        context.startService(OscarTasksService.newIntent(context));
    }

    @WorkerThread
    public static void queueDeleteFcmToken(@NonNull Context context, @NonNull String accessToken, @NonNull String fcmToken) {
        DeleteFcmTokenTask dftt = new DeleteFcmTokenTask(accessToken);
        dftt.fcmToken = fcmToken;
        getQueue(context).offer(dftt);
        context.startService(OscarTasksService.newIntent(context));
    }

    @WorkerThread
    public static void queueDeleteMessage(@NonNull Context context, @NonNull String accessToken, long msgId) {
        DeleteMessageTask dmt = new DeleteMessageTask(accessToken);
        dmt.messageId = msgId;
        getQueue(context).offer(dmt);
        context.startService(OscarTasksService.newIntent(context));
    }

    public static void queueDropPackage(@NonNull Context context, @NonNull String accessToken, @NonNull String hexBoxId, @NonNull EncryptedData pkg) {
        DropPackageTask dpt = new DropPackageTask(accessToken);
        dpt.hexBoxId = hexBoxId;
        dpt.pkg = pkg;
        getQueue(context).offer(dpt);
        context.startService(OscarTasksService.newIntent(context));
    }

    @WorkerThread
    public static void queueSendMessage(@NonNull Context context, @NonNull String accessToken, @NonNull byte[] userId, @NonNull EncryptedData msg, boolean urgent) {
        SendMessageTask smt = new SendMessageTask(accessToken);
        smt.hexUserId = Hex.toHexString(userId);
        smt.message = msg;
        smt.urgent = urgent;
        getQueue(context).offer(smt);
        context.startService(OscarTasksService.newIntent(context));
    }

}
