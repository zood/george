package io.pijun.george.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.format.DateUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;

import io.pijun.george.L;

public class ActivityMonitor extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    @NonNull
    public static Intent newIntent(@NonNull Context ctx) {
        return new Intent(ctx, ActivityMonitor.class);
    }

    private GoogleApiClient mGoogleClient;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        L.i("ActivityMonitor.onStartCommand");
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(ActivityRecognition.API)
                .build();
        mGoogleClient.connect();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        L.i("ActivityMonitor.onDestroy");
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        L.i("ActivityMonitor.onConnected");
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleClient,
                60 * DateUtils.SECOND_IN_MILLIS,
                UserActivityReceiver.getPendingIntent(this));
    }

    @Override
    public void onConnectionSuspended(int i) {
        L.i("ActivityMonitor.onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        L.i("ActivityMonitor.onConnetionFailed: " + connectionResult.getErrorMessage());
    }
}
