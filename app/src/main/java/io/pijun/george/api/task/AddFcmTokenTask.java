package io.pijun.george.api.task;

import java.util.Map;

public class AddFcmTokenTask extends OscarTask {
    public static final transient String NAME = "add_fcm_token";
    public Map<String, String> body;

    public AddFcmTokenTask(String accessToken) {
        super(NAME, accessToken);
    }

    @Override
    public String toString() {
        return "AddFcmTokenTask{" +
                "body=" + body +
                '}';
    }
}
