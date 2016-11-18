package io.pijun.george.api.task;

public abstract class OscarTask {
    public final String apiMethod;

    public OscarTask(String method) {
        this.apiMethod = method;
    }
}
