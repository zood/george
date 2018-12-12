package io.pijun.george.api;

import androidx.annotation.NonNull;

import io.pijun.george.Hex;

public class LoginResponse {

    public byte[] id;
    public String accessToken;
    public byte[] wrappedSymmetricKey;
    public byte[] wrappedSymmetricKeyNonce;

    @NonNull
    @Override
    public String toString() {
        return "LoginResponse{" +
                "id=" + Hex.toHexString(id) +
                ", accessToken='" + accessToken + '\'' +
                ", wrappedSymmetricKey=" + Hex.toHexString(wrappedSymmetricKey) +
                ", wrappedSymmetricKeyNonce=" + Hex.toHexString(wrappedSymmetricKeyNonce) +
                '}';
    }

}
