package io.pijun.george.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GMapsAPI {

    @GET("geocode/json")
    Call<ReverseGeocoding> getReverseGeocoding(@Query("latlng") String latlng, @Query("language") String language);

}
