package io.pijun.george.api;

import io.pijun.george.Hex;

public class AuthenticationChallenge {

    public User user;
    public byte[] challenge;
    public byte[] creationDate;

    @Override
    public String toString() {
        return "AuthenticationChallenge{" +
                "user=" + user +
                ", challenge=" + Hex.toHexString(challenge) +
                ", creationDate=" + Hex.toHexString(creationDate) +
                '}';
    }
}
