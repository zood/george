package io.pijun.george.models;

import io.pijun.george.Hex;

public class FriendRecord {

    public UserRecord user;
    public long id;
    public long userId;
    public byte[] sendingBoxId;
    public byte[] receivingBoxId;

    @Override
    public String toString() {
        return "FriendRecord{" +
                "id=" + id +
                ", userId=" + userId +
                ", sendingBoxId=" + Hex.toHexString(sendingBoxId) +
                ", receivingBoxId=" + Hex.toHexString(receivingBoxId) +
                '}';
    }
}
