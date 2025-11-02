package io.pijun.george.api;

import androidx.annotation.Nullable;

public class LimitedUserInfoUnchecked {

    public String username;
    public byte[] publicKey;

    @Nullable
    public LimitedUserInfo toLimitedUserInfo() {
        if (username != null && publicKey != null) {
            return new LimitedUserInfo(username, publicKey);
        }

        return null;
    }
}
