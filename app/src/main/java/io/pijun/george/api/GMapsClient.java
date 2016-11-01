package io.pijun.george.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GMapsClient {

    private static volatile GMapsAPI sGMapsApi;

    private static GMapsAPI newInstance() {
        String url = "https://maps.googleapis.com/maps/api/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create(OscarClient.sGson))
                .build();

        return retrofit.create(GMapsAPI.class);
    }

    public static GMapsAPI get() {
        if (sGMapsApi == null) {
            synchronized (GMapsClient.class) {
                if (sGMapsApi == null) {
                    sGMapsApi = newInstance();
                }
            }
        }
        return sGMapsApi;
    }

}
