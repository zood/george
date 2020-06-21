package io.pijun.george.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.text.format.DateUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import io.pijun.george.App;
import io.pijun.george.LocationUtils;
import xyz.zood.george.Permissions;

public class PassiveLocationReceiver extends BroadcastReceiver {

    private static final int LOCATION_REQUEST_CODE = 788;

    @AnyThread
    private static Intent newIntent(@NonNull Context context) {
        return new Intent(context, PassiveLocationReceiver.class);
    }

    @AnyThread
    private static PendingIntent newPendingIntent(@NonNull Context context) {
        return PendingIntent.getBroadcast(context,
                LOCATION_REQUEST_CODE,
                newIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    @UiThread
    public void onReceive(Context context, Intent intent) {
//        L.i("PLR onReceive");
        if (!LocationResult.hasResult(intent)) {
//            L.i("PLR intent had no location");
            return;
        }

        LocationResult locRes = LocationResult.extractResult(intent);
        Location loc = locRes.getLastLocation();
        if (loc == null) {
            return;
        }

        // Don't report the location if we're in the foreground, because they'll just be duplicates
        if (App.isInForeground || App.isLimitedShareRunning) {
            return;
        }

        LocationUtils.upload(loc);
    }

    @AnyThread
    public static void register(@NonNull Context context) {
        if (!Permissions.checkGrantedBackgroundLocationPermission(context)) {
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        LocationRequest request = LocationRequest.create();
        request.setInterval(30 * DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_NO_POWER);
        client.requestLocationUpdates(request, newPendingIntent(context));
    }

    @AnyThread
    public static void unregister(@NonNull Context context) {
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.removeLocationUpdates(newPendingIntent(context));
    }
}
