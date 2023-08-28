package io.pijun.george.api.task;

import androidx.annotation.NonNull;
import io.pijun.george.crypto.EncryptedData;

public class SendMessageTask extends OscarTask {
    public static final String NAME = "send_message";
    public String hexUserId;
    public EncryptedData message;
    public boolean urgent;
    public boolean isTransient;

    public SendMessageTask(@NonNull String accessToken) {
        super(NAME, accessToken);
    }

    @Override
    @NonNull
    public String toString() {
        return "SendMessageTask{" +
                "hexUserId='" + hexUserId + '\'' +
                ", message=" + message +
                ", urgent=" + urgent +
                ", transient=" + isTransient +
                '}';
    }
}
