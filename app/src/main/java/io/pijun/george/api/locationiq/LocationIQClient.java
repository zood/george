package io.pijun.george.api.locationiq;

import android.content.Context;

import java.io.IOException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import io.pijun.george.R;
import io.pijun.george.api.OscarClient;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LocationIQClient {

    private static volatile LocationIQAPI sLocationIQAPI;

    @NonNull
    @AnyThread
    private static LocationIQAPI newInstance(@NonNull final String apiKey) {
        String url = "https://locationiq.org/v1/";

        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();

//        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
//        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
//        httpBuilder.addInterceptor(interceptor);

        httpBuilder.addInterceptor(new Interceptor() {
            @Override
            public Response intercept(@NonNull Chain chain) throws IOException {
                Request request = chain.request();
                HttpUrl origUrl = request.url();
                HttpUrl url = origUrl.newBuilder()
                        .addQueryParameter("key", apiKey)
                        .addQueryParameter("format", "json")
                        .build();

                request = request.newBuilder().url(url).build();

                return chain.proceed(request);
            }
        });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create(OscarClient.sGson))
                .client(httpBuilder.build())
                .build();

        return retrofit.create(LocationIQAPI.class);
    }

    @NonNull
    @AnyThread
    public static LocationIQAPI get(@NonNull Context context) {
        if (sLocationIQAPI == null) {
            synchronized (LocationIQClient.class) {
                if (sLocationIQAPI == null) {
                    String apiKey = context.getResources().getString(R.string.locationiq_api_key);
                    sLocationIQAPI = newInstance(apiKey);
                }
            }
        }

        return sLocationIQAPI;
    }

}
