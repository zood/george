package io.pijun.george.models;

import io.pijun.george.Hex;

public class UserRecord {

    public long id;
    public byte[] userId;
    public String username;
    public byte[] publicKey;

    @Override
    public String toString() {
        return "UserRecord{" +
                "id=" + id +
                ", userId=" + Hex.toHexString(userId) +
                ", username='" + username + '\'' +
                ", publicKey=" + Hex.toHexString(publicKey) +
                '}';
    }
}
