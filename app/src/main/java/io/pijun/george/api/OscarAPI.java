package io.pijun.george.api;

import io.pijun.george.crypto.SecretKeyEncryptedMessage;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface OscarAPI {

    @POST("users")
    Call<LoginResponse> createUser(@Body User user);

    @POST("sessions/{username}/challenge-response")
    Call<LoginResponse> completeAuthenticationChallenge(@Path("username") String username, @Body SecretKeyEncryptedMessage response);

    @POST("sessions/{username}/challenge")
    Call<AuthenticationChallenge> getAuthenticationChallenge(@Path("username") String username);

}
