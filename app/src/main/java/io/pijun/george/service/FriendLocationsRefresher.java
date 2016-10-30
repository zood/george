package io.pijun.george.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import io.pijun.george.L;

public class FriendLocationsRefresher extends IntentService {

    public static Intent newIntent(Context context) {
        return new Intent(context, FriendLocationsRefresher.class);
    }

    public FriendLocationsRefresher() {
        super(FriendLocationsRefresher.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        L.i("FLR.onHandleIntent: " + FriendLocationsRefresher.class.getSimpleName());
    }
}
