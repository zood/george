package io.pijun.george.api;

import java.util.Map;

import io.pijun.george.crypto.EncryptedData;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface OscarAPI {

    @POST("users/me/fcm-tokens")
    Call<Void> addFcmToken(@Body Map<String, String> body);

    @POST("sessions/{username}/challenge-response")
    Call<LoginResponse> completeAuthenticationChallenge(@Path("username") String username, @Body FinishedAuthenticationChallenge response);

    @POST("users")
    Call<CreateUserResponse> createUser(@Body User user);

    @DELETE("users/me/fcm-tokens/{fcmToken}")
    Call<Void> deleteFcmToken(@Path("fcmToken") String fcmToken);

    @DELETE("messages/{messageId}")
    Call<Void> deleteMessage(@Path("messageId") long msgId);

    @PUT("drop-boxes/{boxId}")
    Call<Void> dropPackage(@Path("boxId") String hexId, @Body EncryptedData pkg);

    @POST("sessions/{username}/challenge")
    Call<AuthenticationChallenge> getAuthenticationChallenge(@Path("username") String username);

    @GET("users/me/backup")
    Call<EncryptedData> getDatabaseBackup();

    @GET("messages/{msgId}")
    Call<Message> getMessage(@Path("msgId") long msgId);

    @GET("messages")
    Call<Message[]> getMessages();

    @GET("public-key")
    Call<ServerPublicKeyResponse> getServerPublicKey();

    @GET("users/{id}")
    Call<User> getUser(@Path("id") String hexId);

    @GET("drop-boxes/{boxId}")
    Call<EncryptedData> pickUpPackage(@Path("boxId") String hexId);

    @PUT("users/me/backup")
    Call<Void> saveDatabaseBackup(@Body EncryptedData snapshot);

    @GET("users")
    Call<User> searchForUser(@Query("username") String username);

    @POST("users/{userId}/messages")
    Call<Void> sendMessage(@Path("userId") String hexUserId, @Body Map<String, Object> body);

}
