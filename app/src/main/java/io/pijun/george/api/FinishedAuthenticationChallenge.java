package io.pijun.george.api;

import io.pijun.george.crypto.EncryptedData;

public class FinishedAuthenticationChallenge {
    public EncryptedData challenge;
    public EncryptedData creationDate;
}
