package xyz.zood.george.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.WorkerRunnable;
import xyz.zood.george.Permissions;

public class PassiveLocationReceiver extends BroadcastReceiver {

    private static final int LOCATION_REQUEST_CODE = 788;

    private static final Handler handler;
    private static boolean isRunning = false;
    static {
        HandlerThread thread = new HandlerThread(PassiveLocationReceiver.class.getSimpleName());
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @AnyThread
    private static Intent newIntent(@NonNull Context context) {
        return new Intent(context, PassiveLocationReceiver.class);
    }

    @AnyThread
    private static PendingIntent newPendingIntent(@NonNull Context context) {
        return PendingIntent.getBroadcast(context,
                LOCATION_REQUEST_CODE,
                newIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    @Override
    @UiThread
    public void onReceive(Context context, Intent intent) {
        L.i("PLR.onReceive");
        // Don't report the location if we're in the foreground, because they'll just be duplicates
        if (App.isInForeground || App.isLimitedShareRunning) {
            return;
        }

        if (intent == null) {
            return;
        }

        Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
        if (location == null) {
            return;
        }

        LocationUtils.upload(location);
    }

    @AnyThread
    public static void requestUpdates(@NonNull Context context) {
        L.i("PLR.requestUpdates");
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                if (isRunning) {
                    return;
                }

                if (!Permissions.checkBackgroundLocationPermission(context)) {
                    return;
                }

                LocationManager lm = context.getSystemService(LocationManager.class);

                // make sure the device has a 'passive' provider.
                var providers = lm.getAllProviders();
                if (!providers.contains(LocationManager.PASSIVE_PROVIDER)) {
                    // no 'passive' provider so don't request passive location updates
                    return;
                }

                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000, 1, newPendingIntent(context));
                isRunning = true;
            }
        });
    }

    @AnyThread
    public static void stopUpdates(@NonNull Context context) {
        L.i("PLR.stopUpdates");
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    return;
                }

                LocationManager lm = context.getSystemService(LocationManager.class);
                lm.removeUpdates(newPendingIntent(context));
                isRunning = false;
            }
        });
    }
}
