package xyz.zood.george.worker;

import android.content.Context;
import android.location.Location;
import android.text.format.DateUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.pijun.george.App;
import io.pijun.george.AuthenticationManager;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.Prefs;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.service.PositionService;
import xyz.zood.george.Permissions;

public class LocationWorker extends Worker {

    public static final UUID ID = UUID.fromString("f7b3aa9e-4f0b-4eed-a2fb-97a09e48fbc9");

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        L.i("LW.doWork");
        final Context ctx = getApplicationContext();
        if (!AuthenticationManager.isLoggedIn(ctx)) {
            L.i("LW.doWork cancelling because not logged in");
            WorkManager.getInstance(ctx).cancelWorkById(ID);
            return Result.success();
        }

        // only launch the service if the app isn't already in the foreground
        if (App.isInForeground || App.isLimitedShareRunning) {
            L.i("LW.doWork skipping PositionService start, because App.isInForeground || App.isLimitedShareRunning");
            return Result.success();
        }

        // Just return if we already uploaded our location within the last 3 minutes
        long timeSince = Prefs.get(ctx).getLastLocationUpdateTime();
        if (timeSince < 3 * DateUtils.MINUTE_IN_MILLIS) {
            return Result.success();
        }

        if (!Permissions.checkBackgroundLocationPermission(ctx)) {
            return Result.success();
        }

        CountDownLatch latch = new CountDownLatch(1);
        CancellationTokenSource cancelSrc = new CancellationTokenSource();
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                // a needless duplicate check because the lint detector doesn't know that we checked before creating this runnable
                if (!Permissions.checkBackgroundLocationPermission(ctx)) {
                    return;
                }
                FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(ctx);
                Task<Location> task = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelSrc.getToken());
                task.addOnSuccessListener(location -> {
                    if (location == null) {
                        return;
                    }
                    L.i("LW.doWork received location " + location);
                    LocationUtils.upload(location); // asynchronous
                    // give the LocationUtils some time to actually perform the upload
                    try {
                        Thread.sleep(10 * DateUtils.SECOND_IN_MILLIS);
                    } catch (InterruptedException ignored) {
                    }
                    L.i("LW.doWork finished sleeping");
                    latch.countDown();
                });
            }
        });

        try {
            //noinspection ResultOfMethodCallIgnored
            latch.await(PositionService.MAX_WAIT_SECONDS + 10, TimeUnit.SECONDS);
            L.i("LW.doWork passed latch");
        } catch (InterruptedException ignored) {
        } finally {
            cancelSrc.cancel();
        }

        return Result.success();
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
