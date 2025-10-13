package io.pijun.george.api;

import com.google.gson.annotations.SerializedName;

import java.io.Reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.pijun.george.L;
import okhttp3.ResponseBody;
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
    public static final int ERROR_INVALID_EMAIL = 21;
    public static final int ERROR_MISSING_VERIFICATION_TOKEN = 22;
    public static final int ERROR_INVALID_PASSWORD_HASH_ALGORITHM = 23;

    @SerializedName("error_message")
    public String message;

    @SerializedName("error_code")
    public int code;

    @Override
    @NonNull
    public String toString() {
        return "OscarError{" +
                "message='" + message + '\'' +
                ", code=" + code +
                '}';
    }

    @Nullable
    private static OscarError fromReader(@NonNull Reader r) {
        OscarError err = null;
        try {
            err = OscarClient.sGson.fromJson(r, OscarError.class);
        } catch (Throwable t) {
            L.w("can't deserialize OscarError", t);
        }
        return err;
    }

    @Nullable
    public static OscarError fromResponse(@NonNull Response<?> r) {
        try (ResponseBody errBody = r.errorBody()) {
            if (errBody == null) {
                return null;
            }
            return fromReader(errBody.charStream());
        }
    }

    public static OscarError fromResponseBody(ResponseBody rb) {
        if (rb == null) {
            return null;
        }
        return fromReader(rb.charStream());
    }

}
