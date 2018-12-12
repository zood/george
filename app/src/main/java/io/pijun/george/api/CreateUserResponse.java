package io.pijun.george.api;

import androidx.annotation.NonNull;

import io.pijun.george.Hex;

public class CreateUserResponse {
    public byte[] id;

    @NonNull
    @Override
    public String toString() {
        return "CreateUserResponse{" +
                "id=" + Hex.toHexString(id) +
                '}';
    }
}
