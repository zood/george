package io.pijun.george.api;

import androidx.annotation.NonNull;
import io.pijun.george.Hex;

@SuppressWarnings("NullableProblems")
public class AuthenticationChallenge {

    public static class User {
        @NonNull
        public byte[] publicKey;
        @NonNull public byte[] wrappedSecretKey;
        @NonNull public byte[] wrappedSecretKeyNonce;
        @NonNull public byte[] passwordSalt;
        @NonNull public String passwordHashAlgorithm;
        @NonNull public long passwordHashOperationsLimit;
        @NonNull public long passwordHashMemoryLimit;
    }

    @NonNull public User user;
    @NonNull public byte[] challenge;
    @NonNull public byte[] creationDate;

    @Override
    public String toString() {
        return "AuthenticationChallenge{" +
                "user=" + user +
                ", challenge=" + Hex.toHexString(challenge) +
                ", creationDate=" + Hex.toHexString(creationDate) +
                '}';
    }
}
