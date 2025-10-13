package io.pijun.george;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.work.ListenableWorker;

import com.google.common.util.concurrent.SettableFuture;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.LimitedShare;
import io.pijun.george.service.TimedShareService;
import retrofit2.Response;

public class LocationUtils {

    static {
        HandlerThread thread = new HandlerThread("LocationUploader");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                LocationUtils.run();
            }
        });
        // the thread should never quit, because the run() method never exits
        thread.quitSafely();
    }

    @Nullable
    private static UserComm lastLocationMessage = null;
    private static final PriorityBlockingQueue<Location> locationsQueue = new PriorityBlockingQueue<>(5, new Comparator<>() {
        @Override
        public int compare(Location o1, Location o2) {
            // We want the largest timestamp to be at the front/head of the queue
            return (int) (o2.getTime() - o1.getTime());
        }
    });
    private static volatile SettableFuture<ListenableWorker.Result> future;
    private static volatile long futureTime;

    private LocationUtils() {}

    public static String getBestProvider(@NonNull LocationManager lm) {
        var providers = lm.getProviders(true);
        if (providers.isEmpty()) {
            return null;
        }

        if (providers.contains("fused")) { // the constant wasn't introduced until SDK 31
            return "fused";
        }
        if (providers.contains(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }

        return providers.get(0);
    }

    @WorkerThread
    private static void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            Location loc;
            try {
                loc = locationsQueue.take();
            } catch (InterruptedException ex) {
                // should never happen
                L.w("LocationUtils.run interrupted", ex);
                continue;
            }

            if (lastLocationMessage != null && loc.getTime() <= lastLocationMessage.time) {
                L.i("LUtils.run - caught a dupe/old");
                continue;
            }

            Context ctx = App.getApp();
            Prefs prefs = Prefs.get(ctx);
            String token = prefs.getAccessToken();
            KeyPair keyPair = prefs.getKeyPair();
            if (token == null || keyPair == null) {
                L.i("LUtils.run: token or keypair was null, so skipping upload");
                continue;
            }

            Battery.State batteryState = Battery.getState(ctx);
            UserComm locMsg = UserComm.newLocationInfo(loc, prefs.getCurrentMovement(), batteryState.level, batteryState.isCharging);
            byte[] msgBytes = locMsg.toJSON();
            // share to our friends
            ArrayList<FriendRecord> friends = DB.get().getFriendsToShareWith();
            HashMap<String, EncryptedData> pkgs = new HashMap<>(friends.size());
            for (FriendRecord f : friends) {
                EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, f.user.publicKey, keyPair.secretKey);
                if (encMsg == null) {
                    L.w("LUtils.run encryption failed for " + f.user.username);
                    continue;
                }
                pkgs.put(Hex.toHexString(f.sendingBoxId), encMsg);
            }
            LimitedShare ls = DB.get().getLimitedShare();
            if (ls != null) {
                if (TimedShareService.IsRunning) {
                    EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, ls.publicKey, keyPair.secretKey);
                    if (encMsg != null) {
                        pkgs.put(Hex.toHexString(ls.sendingBoxId), encMsg);
                    } else {
                        L.w("LUtils.run: limited share encryption failed");
                    }
                } else {
                    L.i("LUtils.run: oops. the limited share isn't running. we'll delete it.");
                    DB.get().deleteLimitedShares();
                }
            }
            lastLocationMessage = locMsg;   // doesn't matter if we succeed or not
            if (!pkgs.isEmpty()) {
                OscarAPI api = OscarClient.newInstance(token);
                try {
                    Response<Void> response = api.dropMultiplePackages(pkgs).execute();
                    if (response.isSuccessful()) {
                        prefs.setLastLocationUpdateTime(loc.getTime());

                        // check if there is a future to set
                        SettableFuture<ListenableWorker.Result> f = LocationUtils.future;
                        // If there is a future and it's time is less than the time of the message
                        // we just sent, report success.
                        if (f != null && loc.getTime() >= futureTime) {
                            f.set(ListenableWorker.Result.success());
                            LocationUtils.future = null;
                        }
                    } else {
                        OscarError err = OscarError.fromResponse(response);
                        L.w("LUtils.run error dropping packages - " + err);
                        if (err != null && err.code == OscarError.ERROR_INVALID_ACCESS_TOKEN) {
                            if (AuthenticationManager.isLoggedIn(ctx)) {
                                AuthenticationManager.get().logOut(ctx, null);
                            }
                        }
                    }
                } catch (IOException ex) {
                    L.w("LUtils.run - Failed to upload location because " + ex.getLocalizedMessage());
                }
            }

            // now remove any older locations that have piled up
            locationsQueue.removeIf(l -> l.getTime() <= lastLocationMessage.time);
        }
    }

    @AnyThread
    public static void upload(@NonNull Location location) {
        // Apparently lat and/or lng can be infinite: https://github.com/zood/george/issues/119
        if (Double.isInfinite(location.getLatitude()) || Double.isInfinite(location.getLongitude())) {
            return;
        }
        locationsQueue.add(location);
    }

    @AnyThread
    public static void uploadFuture(@NonNull Location location, @NonNull SettableFuture<ListenableWorker.Result> future) {
        LocationUtils.future = future;
        LocationUtils.futureTime = location.getTime();
        locationsQueue.add(location);
    }
}
