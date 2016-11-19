package io.pijun.george.api.task;

import io.pijun.george.crypto.EncryptedData;

public class SendMessageTask extends OscarTask {
    public static final transient String NAME = "send_message";
    public String hexUserId;
    public EncryptedData message;

    public SendMessageTask() {
        super(NAME);
    }

    @Override
    public String toString() {
        return "SendMessageTask{" +
                "hexUserId='" + hexUserId + '\'' +
                ", message=" + message +
                '}';
    }
}
