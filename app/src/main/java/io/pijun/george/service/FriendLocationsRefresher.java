package io.pijun.george.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import io.pijun.george.L;

public class FriendLocationsRefresher extends IntentService {

    public static Intent newIntent(Context context) {
        // TODO: What was my intention when building this class? Is it obviated by PackageWatcher?
        return new Intent(context, FriendLocationsRefresher.class);
    }

    public FriendLocationsRefresher() {
        super(FriendLocationsRefresher.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }
}
