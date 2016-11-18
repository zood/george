package io.pijun.george.api.task;

public class DeleteMessageTask extends OscarTask {
    public static final transient String NAME = "delete_message";
    public long messageId;

    public DeleteMessageTask() {
        super(NAME);
    }
}
