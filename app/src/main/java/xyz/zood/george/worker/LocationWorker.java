package xyz.zood.george.worker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.text.format.DateUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import androidx.work.Constraints;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;

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

public class LocationWorker extends ListenableWorker implements LocationListenerCompat {

    public static final UUID ID = UUID.fromString("f7b3aa9e-4f0b-4eed-a2fb-97a09e48fbc9");

    private SettableFuture<Result> future;
    LocationManager locMgr;

    public LocationWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
//        L.i("LW.startWork");
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
        if (timeSince < 2 * DateUtils.MINUTE_IN_MILLIS) {
            future.set(Result.success());
            return future;
        }

        if (!Permissions.checkBackgroundLocationPermission(ctx)) {
            future.set(Result.success());
            return future;
        }

        locMgr = ctx.getSystemService(LocationManager.class);
        if (locMgr == null) {
            future.set(Result.success());
            return future;
        }
        String provider = LocationUtils.getBestProvider(locMgr);
        if (provider == null) {
            future.set(Result.success());
            return future;
        }

        LocationRequestCompat req = new LocationRequestCompat.Builder(0).setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build();
        LocationManagerCompat.requestLocationUpdates(locMgr, provider, req, App.getExecutor(), this);

        return future;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onLocationChanged(@NonNull Location location) {
        LocationUtils.uploadFuture(location, future); // asynchronous
        LocationManagerCompat.removeUpdates(locMgr, this);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onStopped() {
        super.onStopped();

        L.i("LW.onStopped called");
        if (locMgr != null) {
            LocationManagerCompat.removeUpdates(locMgr, this);
        }
        if (future != null) {
            if (!future.isDone()) {
                future.set(Result.failure());
            }
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
