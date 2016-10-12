package io.pijun.george.crypto;

public interface KeyReply {

    public void onKey(byte[] userId);
    public void onFailure();

}
