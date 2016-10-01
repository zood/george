package io.pijun.george.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.pijun.george.api.adapter.BytesToBase64Adapter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OscarClient {

    public static OscarAPI newInstance() {
        String url = "http://192.168.1.76:9999/alpha/";
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeHierarchyAdapter(byte[].class, new BytesToBase64Adapter())
                .create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return retrofit.create(OscarAPI.class);
    }

}
