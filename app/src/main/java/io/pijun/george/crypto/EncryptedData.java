package io.pijun.george.crypto;

import io.pijun.george.Hex;

public class EncryptedData {

    public byte[] cipherText;
    public byte[] nonce;

    @Override
    public String toString() {
        return "EncryptedData:\n" + "cipherText: " +
                Hex.toHexString(cipherText) +
                '\n' +
                "nonce: " +
                Hex.toHexString(nonce);
    }
}
