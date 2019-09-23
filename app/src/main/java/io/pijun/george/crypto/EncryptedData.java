package io.pijun.george.crypto;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import io.pijun.george.Hex;

/**
 * We have to manually specify @Keep here because EncryptedData objects
 * get constructed from JNI.
 */
@Keep
public class EncryptedData {

    public byte[] cipherText;
    public byte[] nonce;

    @NonNull
    @Override
    public String toString() {
        return "EncryptedData:\n" + "cipherText: " +
                Hex.toHexString(cipherText) +
                '\n' +
                "nonce: " +
                Hex.toHexString(nonce);
    }
}
