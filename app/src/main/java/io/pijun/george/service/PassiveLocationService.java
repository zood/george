package io.pijun.george.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import io.pijun.george.App;

public class PassiveLocationService extends IntentService {

    private static final int LOCATION_REQUEST_CODE = 788;

    public PassiveLocationService() {
        super("PassiveLocationService");
    }

    @AnyThread
    private static Intent newIntent(@NonNull Context context) {
        return new Intent(context, PassiveLocationService.class);
    }

    @AnyThread
    private static PendingIntent newPendingIntent(@NonNull Context context) {
        return PendingIntent.getService(context,
                LOCATION_REQUEST_CODE,
                newIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (!LocationResult.hasResult(intent)) {
            return;
        }

        LocationResult locRes = LocationResult.extractResult(intent);
        Location loc = locRes.getLastLocation();
        if (loc == null) {
            return;
        }

        // Don't report the location if we're in the foreground, because they'll just be duplicates
        if (!App.isInForeground) {
            App.postOnBus(loc);
        }
    }

    public static void register(@NonNull Context context) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        LocationRequest request = LocationRequest.create();
        request.setInterval(30 * DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_NO_POWER);
        client.requestLocationUpdates(request, newPendingIntent(context));
    }

    public static void unregister(@NonNull Context context) {
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.removeLocationUpdates(newPendingIntent(context));
    }

}
