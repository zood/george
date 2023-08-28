package io.pijun.george.api.task;

public class DeleteFcmTokenTask extends OscarTask {
    public static final String NAME = "delete_fcm_token";
    public String fcmToken;

    public DeleteFcmTokenTask(String accessToken) {
        super(NAME, accessToken);
    }
}
