package io.pijun.george.models;

public class GrantedShare {

    public String username;
    public byte[] userId;
    public byte[] boxId;

    @Override
    public String toString() {
        return "GrantedShare{" +
                "username='" + username + '\'' +
                '}';
    }
}
