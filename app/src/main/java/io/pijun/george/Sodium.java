package io.pijun.george;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;

public class Sodium {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    public native static int init();

    @CheckResult
    public native static int getPasswordHashSaltLength();

    @CheckResult
    public native static int generateKeyPair(KeyPair kp);

    @CheckResult
    public native static int getSymmetricKeyLength();

    @CheckResult
    @Nullable
    public native static EncryptedData symmetricKeyEncrypt(@NonNull byte[] msg, @NonNull byte[] key);

    @CheckResult
    @Nullable
    public native static byte[] symmetricKeyDecrypt(@NonNull byte[] cipherText, @NonNull byte[] nonce, @NonNull byte[] key);

    @CheckResult
    @Nullable
    public native static EncryptedData publicKeyEncrypt(@NonNull byte[] msg, @NonNull byte[] receiverPubKey, @NonNull byte[] senderSecretKey);

    @CheckResult
    @Nullable
    public native static byte[] publicKeyDecrypt(@NonNull byte[] cipherText, @NonNull byte[] nonce, @NonNull byte[] senderPubKey, @NonNull byte[] receiverSecretKey);

    @CheckResult
    @Nullable
    public native static byte[] stretchPassword(int hashSizeBytes, @NonNull byte[] password, @NonNull byte[] salt, int algId, long opsLimit, long memLimit);

}
