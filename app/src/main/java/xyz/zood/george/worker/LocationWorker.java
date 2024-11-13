package xyz.zood.george.worker;

import android.content.Context;
import android.location.Location;
import android.text.format.DateUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.time.Duration;
import java.util.UUID;

import io.pijun.george.App;
import io.pijun.george.AuthenticationManager;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.Prefs;
import xyz.zood.george.Permissions;

public class LocationWorker extends ListenableWorker {

    public static final UUID ID = UUID.fromString("f7b3aa9e-4f0b-4eed-a2fb-97a09e48fbc9");

    private final CancellationTokenSource cancel = new CancellationTokenSource();
    private final FusedLocationProviderClient client;
    private SettableFuture<Result> future;

    public LocationWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
        client = LocationServices.getFusedLocationProviderClient(ctx);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        future = SettableFuture.create();

        final Context ctx = getApplicationContext();
        if (!AuthenticationManager.isLoggedIn(ctx)) {
            L.i("LW.doWork cancelling because not logged in");
            WorkManager.getInstance(ctx).cancelWorkById(ID);
            future.set(Result.success());
            return future;
        }

        if (App.isInForeground || App.isLimitedShareRunning) {
            L.i("LW.doWork returning from startWork() because because App.isInForeground || App.isLimitedShareRunning");
            future.set(Result.success());
            return future;
        }

        long timeSince = Prefs.get(ctx).getLastLocationUpdateTime();
        if (timeSince < 3 * DateUtils.MINUTE_IN_MILLIS) {
            future.set(Result.success());
            return future;
        }

        if (!Permissions.checkBackgroundLocationPermission(ctx)) {
            future.set(Result.success());
            return future;
        }

        Task<Location> task = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.getToken());
        task.addOnSuccessListener(location -> {
            if (location == null) {
                future.set(Result.failure());
                return;
            }

            LocationUtils.uploadFuture(location, future); // asynchronous
        });

        task.addOnFailureListener(ex -> {
            L.i("LW failed to obtain a location: " + ex.getLocalizedMessage());
            future.setException(ex);
            future.set(Result.failure());
        });

        task.addOnCanceledListener(() -> {
            L.i("LW getCurrentLocation canceled");
            future.set(Result.failure());
        });

        return future;
    }

    @Override
    public void onStopped() {
        super.onStopped();

        L.i("LW.onStopped called");
        cancel.cancel();
        if (!future.isDone()) {
            future.set(Result.failure());
        }
    }

    @AnyThread
    public static void scheduleLocationWorker(@NonNull Context ctx) {
        Constraints constraints = new Constraints.Builder().
                setRequiredNetworkType(NetworkType.CONNECTED).
                setRequiresCharging(false).
                setRequiresDeviceIdle(false).
                build();
        WorkRequest req = new PeriodicWorkRequest.Builder(LocationWorker.class,
                Duration.ofMinutes(15)).
                setConstraints(constraints).
                setId(ID).
                build();
        WorkManager.getInstance(ctx).enqueue(req);
    }

    @AnyThread
    public static void unscheduleLocationWorker(@NonNull Context ctx) {
        WorkManager.getInstance(ctx).cancelWorkById(ID);
    }
}
