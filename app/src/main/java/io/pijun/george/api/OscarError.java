package io.pijun.george.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.Reader;

import io.pijun.george.L;
import retrofit2.Response;

@SuppressWarnings("unused")
public class OscarError {

    public static int ERROR_NONE = 0;
    public static int ERROR_INTERNAL = 1;
    public static int ERROR_BAD_REQUEST = 2;
    public static int ERROR_INVALID_USERNAME = 3;
    public static int ERROR_INVALID_PUBLIC_KEY = 4;
    public static int ERROR_INVALID_WRAPPED_SECRET_KEY = 5;
    public static int ERROR_INVALID_WRAPPED_SECRET_KEY_NONCE = 6;
    public static int ERROR_INVALID_WRAPPED_SYMMETRIC_KEY = 7;
    public static int ERROR_INVALID_WRAPPED_SYMMETRIC_KEY_NONCE = 8;
    public static int ERROR_INVALID_PASSWORD_SALT = 9;
    public static int ERROR_USERNAME_NOT_AVAILABLE = 10;
    public static int ERROR_NOT_FOUND = 11;
    public static int ERROR_INSUFFICIENT_PERMISSION = 12;
    public static int ERROR_ARGON2I_OPS_LIMIT_TOO_LOW = 13;
    public static int ERROR_ARGON2I_MEM_LIMIT_TOO_LOW = 14;
    public static int ERROR_INVALID_ACCESS_TOKEN = 15;
    public static int ERROR_USER_NOT_FOUND = 16;
    public static int ERROR_CHALLENGE_NOT_FOUND = 17;
    public static int ERROR_CHALLENGE_EXPIRED = 18;
    public static int ERROR_LOGIN_FAILED = 19;

    @SerializedName("error_message")
    public String message;

    @SerializedName("error_code")
    public int code;

    @Override
    public String toString() {
        return "OscarError{" +
                "message='" + message + '\'' +
                ", code=" + code +
                '}';
    }

    public static OscarError fromReader(Reader r) {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        OscarError err = null;
        try {
            err = gson.fromJson(r, OscarError.class);
        } catch (Throwable t) {
            L.w("can't deserialize OscarError", t);
        }
        return err;
    }

    public static OscarError fromResponse(Response r) {
        return fromReader(r.errorBody().charStream());
    }

}
