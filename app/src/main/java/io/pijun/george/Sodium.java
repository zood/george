package io.pijun.george;

import io.pijun.george.crypto.KeyPair;
import io.pijun.george.crypto.PKEncryptedMessage;

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
    public native static byte[] createHashFromPassword(int hashSizeBytes, byte[] password, byte[] salt, long opsLimit, long memLimit);

    public native static int getPasswordHashSaltLength();

    public native static int generateKeyPair(KeyPair kp);

    public native static int getSymmetricKeyLength();

    public native static PKEncryptedMessage symmetricKeyEncrypt(byte[] msg, byte[] key);

    public native static byte[] symmetricKeyDecrypt(byte[] cipherText, byte[] nonce, byte[] key);

    public native static PKEncryptedMessage publicKeyEncrypt(byte[] msg, byte[] receiverPubKey, byte[] senderSecretKey);

    public native static byte[] publicKeyDecrypt(byte[] cipherText, byte[] nonce, byte[] senderPubKey, byte[] receiverSecretKey);

//    public native static byte[] createHash(byte[] data, byte[] key);

}
