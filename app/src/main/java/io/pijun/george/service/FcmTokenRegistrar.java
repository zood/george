package io.pijun.george.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.google.firebase.iid.FirebaseInstanceId;

import java.io.IOException;
import java.util.Collections;

import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import retrofit2.Response;

public class FcmTokenRegistrar extends IntentService {

    private static final String ARG_UNREGISTER = "unregister";
    private static final String ARG_ACCESS_TOKEN = "access_token";

    public static Intent newIntent(Context ctx) {
        return newIntent(ctx, false, null);
    }

    public static Intent newIntent(Context ctx, boolean unregister, String accessToken) {
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
        if (intent.getBooleanExtra(ARG_UNREGISTER, false)) {
            unregister(intent.getStringExtra(ARG_ACCESS_TOKEN));
        } else {
            register();
        }
    }

    @WorkerThread
    private void register() {
        L.i("FTR.register");
        String fcmToken = FirebaseInstanceId.getInstance().getToken();
        if (fcmToken == null) {
            return;
        }

        L.i("|  token: " + fcmToken);
        Prefs prefs = Prefs.get(this);
        String savedFcmToken = prefs.getFcmToken();
        // check if we've already uploaded this token
        if (savedFcmToken != null && savedFcmToken.equals(fcmToken)) {
            return;
        }

        // if someone is logged in, perform the upload
        String apiAccessToken = prefs.getAccessToken();
        if (!prefs.isLoggedIn()) {
            return;
        }
        OscarAPI api = OscarClient.newInstance(apiAccessToken);
        try {
            Response<Void> response = api.addFcmToken(Collections.singletonMap("token", fcmToken)).execute();
            if (!response.isSuccessful()) {
                L.i("problem uploading fcm token: " + OscarError.fromResponse(response));
                return;
            }
        } catch (IOException ex) {
            L.w("serious error adding fcm token", ex);
            return;
        }

        // upon success, save the token to our prefs
        prefs.setFcmToken(fcmToken);
    }

    @WorkerThread
    private void unregister(@NonNull String accessToken) {
        String fcmToken = FirebaseInstanceId.getInstance().getToken();
        if (fcmToken == null) {
            return;
        }

        OscarAPI api = OscarClient.newInstance(accessToken);
        try {
            Response<Void> response = api.deleteFcmToken(fcmToken).execute();
            if (!response.isSuccessful()) {
                L.i("problem deleting fcm token: " + OscarError.fromResponse(response));
            }
        } catch (IOException ex) {
            L.w("serious error deleting fcm token", ex);
        }

        Prefs.get(this).setFcmToken(null);
    }

}
