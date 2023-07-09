package xyz.zood.george.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.DateUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.ExecutionException;

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
        L.i("PLR onReceive");
        if (!LocationResult.hasResult(intent)) {
//            L.i("PLR intent had no location");
            return;
        }

        LocationResult locRes = LocationResult.extractResult(intent);
        if (locRes == null) {
            return;
        }
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

    @WorkerThread
    private static void _requestUpdates(@NonNull Context context) {
        if (isRunning) {
            return;
        }
        if (!Permissions.checkGrantedBackgroundLocationPermission(context)) {
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        LocationRequest request = new LocationRequest.Builder(30 * DateUtils.SECOND_IN_MILLIS).
                setPriority(Priority.PRIORITY_PASSIVE).
                build();
        Task<Void> task = client.requestLocationUpdates(request, newPendingIntent(context));
        try {
            Tasks.await(task);
            isRunning = true;
            L.i("Successfully requested passive location updates");
        } catch (ExecutionException e) {
            L.w("Failed to request passive location updates", e);
        } catch (InterruptedException e) {
            L.w("Interrupted while requesting passive location updates", e);
        }
    }

    @AnyThread
    public static void requestUpdates(@NonNull Context context) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                _requestUpdates(context);
            }
        });
    }

    @WorkerThread
    private static void _stopUpdates(@NonNull Context context) {
        if (!isRunning) {
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        Task<Void> task = client.removeLocationUpdates(newPendingIntent(context));
        try {
            Tasks.await(task);
            isRunning = false;
            L.i("Successfully stopped passive location updates");
        } catch (ExecutionException e) {
            L.w("Failed to stop passive location updates", e);
        } catch (InterruptedException e) {
            L.w("Interrupted while stopping passive location updates", e);
        }
    }

    @AnyThread
    public static void stopUpdates(@NonNull Context context) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                _stopUpdates(context);
            }
        });
    }
}
