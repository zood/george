package io.pijun.george.crypto;

import androidx.annotation.NonNull;

import io.pijun.george.Hex;

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
