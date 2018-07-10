package io.pijun.george.api;

import android.support.annotation.NonNull;

@SuppressWarnings("NullableProblems")
public class SearchUserResult {

    @NonNull public byte[] id;
    @NonNull public String username;
    @NonNull public byte[] publicKey;

}
