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

    public final static long PASSWORDHASH_OPSLIMIT_INTERACTIVE = 4;
    public final static long PASSWORDHASH_OPSLIMIT_MODERATE = 6;
    public final static long PASSWORDHASH_OPSLIMIT_SENSITIVE = 8;

    public final static long PASSWORDHASH_MEMLIMIT_INTERACTIVE = 33554432;
    public final static long PASSWORDHASH_MEMLIMIT_MODERATE = 134217728;
    public final static long PASSWORDHASH_MEMLIMIT_SENSITIVE = 536870912;

    /**
     * Deterministically generates a 32-byte cryptographic key from a password and salt.
     * @param password An arbitrarily long password
     * @param salt An array of random bytes. The necessary length can be found by calling
     *             getPasswordHashSaltLength().
     * @param opsLimit one of the PASSWORDHASH_OPSLIMIT_* values
     * @param memLimit one of the PASSWORDHASH_MEMLIMIT_* values
     * @return a 32-byte key
     */
    @CheckResult
    @Nullable
    public native static byte[] createHashFromPassword(int hashSizeBytes, @NonNull byte[] password, @NonNull byte[] salt, long opsLimit, long memLimit);

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

}
