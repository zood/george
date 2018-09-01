package io.pijun.george.api;

import androidx.annotation.NonNull;
import io.pijun.george.crypto.EncryptedData;

public class FinishedAuthenticationChallenge {
    public EncryptedData challenge;
    public EncryptedData creationDate;

    @Override
    @NonNull
    public String toString() {
        return "FinishedAuthenticationChallenge{" +
                "challenge=" + challenge +
                ", creationDate=" + creationDate +
                '}';
    }
}
