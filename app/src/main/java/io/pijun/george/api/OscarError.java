package io.pijun.george.api;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.Reader;

import io.pijun.george.L;
import retrofit2.Response;

@SuppressWarnings("unused")
public class OscarError {

    public static final int ERROR_NONE = 0;
    public static final int ERROR_INTERNAL = 1;
    public static final int ERROR_BAD_REQUEST = 2;
    public static final int ERROR_INVALID_USERNAME = 3;
    public static final int ERROR_INVALID_PUBLIC_KEY = 4;
    public static final int ERROR_INVALID_WRAPPED_SECRET_KEY = 5;
    public static final int ERROR_INVALID_WRAPPED_SECRET_KEY_NONCE = 6;
    public static final int ERROR_INVALID_WRAPPED_SYMMETRIC_KEY = 7;
    public static final int ERROR_INVALID_WRAPPED_SYMMETRIC_KEY_NONCE = 8;
    public static final int ERROR_INVALID_PASSWORD_SALT = 9;
    public static final int ERROR_USERNAME_NOT_AVAILABLE = 10;
    public static final int ERROR_NOT_FOUND = 11;
    public static final int ERROR_INSUFFICIENT_PERMISSION = 12;
    public static final int ERROR_ARGON2I_OPS_LIMIT_TOO_LOW = 13;
    public static final int ERROR_ARGON2I_MEM_LIMIT_TOO_LOW = 14;
    public static final int ERROR_INVALID_ACCESS_TOKEN = 15;
    public static final int ERROR_USER_NOT_FOUND = 16;
    public static final int ERROR_CHALLENGE_NOT_FOUND = 17;
    public static final int ERROR_CHALLENGE_EXPIRED = 18;
    public static final int ERROR_LOGIN_FAILED = 19;
    public static final int ERROR_BACKUP_NOT_FOUND = 20;

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

    @Nullable
    private static OscarError fromReader(Reader r) {
        OscarError err = null;
        try {
            err = OscarClient.sGson.fromJson(r, OscarError.class);
        } catch (Throwable t) {
            L.w("can't deserialize OscarError", t);
        }
        return err;
    }

    @Nullable
    public static OscarError fromResponse(Response r) {
        return fromReader(r.errorBody().charStream());
    }

}
