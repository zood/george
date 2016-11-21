package io.pijun.george.api.task;

public abstract class OscarTask {
    public final String apiMethod;
    public final String accessToken;

    public OscarTask(String method, String accessToken) {
        this.apiMethod = method;
        this.accessToken = accessToken;
    }
}
