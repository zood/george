package io.pijun.george.api.task;

import io.pijun.george.crypto.EncryptedData;

public class SendMessageTask extends OscarTask {
    public static final transient String NAME = "send_message";
    public String hexUserId;
    public EncryptedData message;
    public boolean urgent;

    public SendMessageTask(String accessToken) {
        super(NAME, accessToken);
    }

    @Override
    public String toString() {
        return "SendMessageTask{" +
                "hexUserId='" + hexUserId + '\'' +
                ", message=" + message +
                ", urgent=" + urgent +
                '}';
    }
}
