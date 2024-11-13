package io.pijun.george.database;

import androidx.annotation.NonNull;

import io.pijun.george.Hex;

public class FriendRecord {

    public UserRecord user;
    public long id;
    public long userId;
    public byte[] sendingBoxId;
    public byte[] receivingBoxId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FriendRecord that = (FriendRecord) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @NonNull
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
