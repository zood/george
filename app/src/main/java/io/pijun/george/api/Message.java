package io.pijun.george.api;

import io.pijun.george.Hex;

public class Message {

    public long id;
    public byte[] senderId;
    public byte[] cipherText;
    public byte[] nonce;
    public long sentDate;

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", senderId=" + Hex.toHexString(senderId) +
                ", cipherText=" + Hex.toHexString(cipherText) +
                ", nonce=" + Hex.toHexString(nonce) +
                ", sentDate=" + sentDate +
                '}';
    }
}
