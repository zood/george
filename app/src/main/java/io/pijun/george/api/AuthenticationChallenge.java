package io.pijun.george.api;

import io.pijun.george.Hex;

public class AuthenticationChallenge {

    public User user;
    public byte[] challenge;
    public byte[] publicKey;

    @Override
    public String toString() {
        return "AuthenticationChallenge{" +
                "user=" + user +
                ", challenge=" + Hex.toHexString(challenge) +
                ", publicKey=" + Hex.toHexString(publicKey) +
                '}';
    }
}
