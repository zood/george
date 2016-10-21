package io.pijun.george.api;

import io.pijun.george.crypto.PKEncryptedMessage;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface OscarAPI {

    @POST("sessions/{username}/challenge-response")
    Call<LoginResponse> completeAuthenticationChallenge(@Path("username") String username, @Body PKEncryptedMessage response);

    @POST("users")
    Call<LoginResponse> createUser(@Body User user);

    @PUT("drop-boxes/{boxId}")
    Call<Void> dropPackage(@Path("boxId") String hexId, @Body PKEncryptedMessage pkg);

    @POST("sessions/{username}/challenge")
    Call<AuthenticationChallenge> getAuthenticationChallenge(@Path("username") String username);

    @GET("messages")
    Call<Message[]> getMessages();

    @GET("users/{id}")
    Call<User> getUser(@Path("id") String hexId);

    @GET("users")
    Call<User> searchForUser(@Query("username") String username);

    @POST("users/{userId}/messages")
    Call<Void> sendMessage(@Path("userId") String hexUserId, @Body PKEncryptedMessage msg);

}
