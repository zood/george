package io.pijun.george.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LocationIQAPI {

    @GET("reverse.php")
    Call<RevGeocoding> getReverseGeocoding(@Query("lat") String lat, @Query("lon") String lon);

}
