package io.pijun.george.models;

public class FriendRecord {

    public long id;
    public String username;
    public byte[] userId;
    public byte[] publicKey;
    public byte[] sendingBoxId;
    public byte[] receivingBoxId;
    public String shareRequestNote;

    @Override
    public String toString() {
        return "FriendRecord{" +
                "id=" + id +
                ", username='" + username + '\'' +
                '}';
    }
}
