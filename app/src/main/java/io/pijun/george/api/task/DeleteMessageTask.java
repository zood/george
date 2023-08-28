package io.pijun.george.api.task;

public class DeleteMessageTask extends OscarTask {
    public static final String NAME = "delete_message";
    public long messageId;

    public DeleteMessageTask(String accessToken) {
        super(NAME, accessToken);
    }
}
