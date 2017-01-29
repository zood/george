package io.pijun.george.api;

import io.pijun.george.Hex;

public class CreateUserResponse {
    public byte[] id;

    @Override
    public String toString() {
        return "CreateUserResponse{" +
                "id=" + Hex.toHexString(id) +
                '}';
    }
}
