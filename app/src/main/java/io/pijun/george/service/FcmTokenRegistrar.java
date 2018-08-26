package io.pijun.george.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.api.OscarClient;

public class FcmTokenRegistrar extends IntentService {

    private static final String ARG_UNREGISTER = "unregister";
    private static final String ARG_ACCESS_TOKEN = "access_token";

    public static Intent newIntent(Context ctx) {
        return newIntent(ctx, false, null);
    }

    public static Intent newIntent(Context ctx, boolean unregister, @Nullable String accessToken) {
        Intent i = new Intent(ctx, FcmTokenRegistrar.class);
        if (unregister && accessToken == null) {
            throw new IllegalArgumentException("You must provide an access token when you want to unregister");
        }
        i.putExtra(ARG_UNREGISTER, unregister);
        if (accessToken != null) {
            i.putExtra(ARG_ACCESS_TOKEN, accessToken);
        }

        return i;
    }

    public FcmTokenRegistrar() {
        super(FcmTokenRegistrar.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        L.i("FcmTokenRegistrar.onHandleIntent");
        if (intent.getBooleanExtra(ARG_UNREGISTER, false)) {
            unregister(intent.getStringExtra(ARG_ACCESS_TOKEN));
        } else {
            register();
        }
    }

    @WorkerThread
    private void register() {
        String fcmToken = FirebaseInstanceId.getInstance().getToken();
        if (fcmToken == null) {
            return;
        }

        Prefs prefs = Prefs.get(this);
        String savedFcmToken = prefs.getFcmToken();
        // check if we've already uploaded this token
        if (savedFcmToken != null && savedFcmToken.equals(fcmToken)) {
            return;
        }

        // if we're not logged in, return
        String apiAccessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(apiAccessToken)) {
            return;
        }

        OscarClient.queueAddFcmToken(this, apiAccessToken, Collections.singletonMap("token", fcmToken));

        // upon success, save the token to our prefs
        prefs.setFcmToken(fcmToken);
    }

    @WorkerThread
    private void unregister(@NonNull String accessToken) {
        String fcmToken = FirebaseInstanceId.getInstance().getToken();
        if (fcmToken == null) {
            return;
        }

        OscarClient.queueDeleteFcmToken(this, accessToken, fcmToken);

        Prefs.get(this).setFcmToken(null);
    }

}
