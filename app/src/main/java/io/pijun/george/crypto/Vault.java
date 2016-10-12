package io.pijun.george.crypto;

import android.content.Context;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import retrofit2.Response;

public class Vault {

    private static final ConcurrentHashMap<String, byte[]> mSavedKeys = new ConcurrentHashMap<>();

    public static byte[] getPublicKey(Context context, byte[] userId) throws IOException {
        if (userId == null) {
            throw new IllegalArgumentException("user id can't be null");
        }
        String hexId = Hex.toHexString(userId);

        byte[] key = mSavedKeys.get(hexId);
        if (key != null) {
            return key;
        }

        Response<User> response = OscarClient.newInstance(Prefs.get(context)
                .getAccessToken())
                .getUser(hexId)
                .execute();
        if (response.isSuccessful()) {
            // save the key to our cache before returning
            User user = response.body();

            mSavedKeys.put(hexId, user.publicKey);
            return user.publicKey;
        }

        OscarError err = OscarError.fromResponse(response);
        L.i("error retrieving public key: " + err);
        return null;
    }

}
