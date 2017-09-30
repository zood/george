package io.pijun.george.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LocationIQAPI {

    @GET("reverse.php")
    Call<RevGeocoding> getReverseGeocoding(@Query("lat") String lat, @Query("lon") String lon);

    @GET("reverse.php")
    Call<ResponseBody> getReverseGeocoding2(@Query("lat") String lat, @Query("lon") String lon);

}
