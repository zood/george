package io.pijun.george.models;

import java.util.Arrays;

import io.pijun.george.Hex;

public class FriendRecord {

    public long id;
    public String username;
    public byte[] userId;
    public byte[] publicKey;
    public byte[] sendingBoxId;
    public byte[] receivingBoxId;
    public boolean shareRequested;
    public String shareRequestNote;

    @Override
    public String toString() {
        return "FriendRecord{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", userId=" + Hex.toHexString(userId) +
                ", publicKey=" + Hex.toHexString(publicKey) +
                ", sendingBoxId=" + Hex.toHexString(sendingBoxId) +
                ", receivingBoxId=" + Hex.toHexString(receivingBoxId) +
                ", shareRequested=" + shareRequested +
                ", shareRequestNote='" + shareRequestNote + '\'' +
                '}';
    }
}
