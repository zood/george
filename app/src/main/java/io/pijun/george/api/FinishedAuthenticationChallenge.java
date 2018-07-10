package io.pijun.george.api;

import io.pijun.george.crypto.EncryptedData;

public class FinishedAuthenticationChallenge {
    public EncryptedData challenge;
    public EncryptedData creationDate;

    @Override
    public String toString() {
        return "FinishedAuthenticationChallenge{" +
                "challenge=" + challenge +
                ", creationDate=" + creationDate +
                '}';
    }
}
