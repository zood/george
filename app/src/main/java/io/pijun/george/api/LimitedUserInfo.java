package io.pijun.george.api;

import androidx.annotation.NonNull;

public class LimitedUserInfo {

    @NonNull final public String username;
    @NonNull final public byte[] publicKey;

    public LimitedUserInfo(@NonNull String username, @NonNull byte[] publicKey) {
        this.username = username;
        this.publicKey = publicKey;
    }
}
