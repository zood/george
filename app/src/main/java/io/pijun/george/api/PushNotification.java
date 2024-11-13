package io.pijun.george.api;

import androidx.annotation.Nullable;
import io.pijun.george.Constants;

public class PushNotification {
    public @Nullable String id;
    public byte[] cipherText;
    public byte[] nonce;
    public byte[] senderId;
    public String sentDate;

    public boolean isValid() {
        if (cipherText == null || cipherText.length == 0) {
            return false;
        }
        if (nonce == null || nonce.length == 0) {
            return false;
        }
        if (senderId == null || senderId.length != Constants.USER_ID_LENGTH) {
            return false;
        }
        return sentDate != null;
    }
}
