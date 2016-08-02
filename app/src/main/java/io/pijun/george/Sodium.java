package io.pijun.george;

import io.pijun.george.crypto.KeyPair;
import io.pijun.george.crypto.SecretKeyEncryptedMessage;

public class Sodium {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    public native static int init();

    public native static byte[] createKeyFromPassword(int keySizeBytes, byte[] password, byte[] salt);

    public native static int generateKeyPair(KeyPair kp);

    public native static SecretKeyEncryptedMessage secretKeyEncrypt(byte[] msg, byte[] key);

    public native static byte[] secretKeyDecrypt(byte[] cipherText, byte[] nonce, byte[] key);

    public native static SecretKeyEncryptedMessage publicKeyEncrypt(byte[] msg, byte[] receiverPubKey, byte[] senderSecretKey);

    public native static byte[] publicKeyDecrypt(byte[] cipherText, byte[] nonce, byte[] senderPubKey, byte[] receiverSecretKey);

}
