package io.pijun.george;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.LimitedShare;
import io.pijun.george.service.LimitedShareService;
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

    @Nullable private static UserComm lastLocationMessage = null;
    private static PriorityBlockingQueue<Location> locationsQueue = new PriorityBlockingQueue<>(5, new Comparator<Location>() {
        @Override
        public int compare(Location o1, Location o2) {
            // We want the largest timestamp to be at the front/head of the queue
            return (int) (o2.getTime() - o1.getTime());
        }
    });

    private LocationUtils() {}

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

            Integer batteryLevel = Battery.getLevel(ctx);
            if (batteryLevel == -1) {
                batteryLevel = null;
            }
            UserComm locMsg = UserComm.newLocationInfo(loc, prefs.getCurrentMovement(), batteryLevel);
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
                L.i("LUtils.upload: to limited share");
                if (LimitedShareService.IsRunning) {
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
            if (pkgs.size() > 0) {
                OscarAPI api = OscarClient.newInstance(token);
                try {
                    Response<Void> response = api.dropMultiplePackages(pkgs).execute();
                    if (response.isSuccessful()) {
                        prefs.setLastLocationUpdateTime(loc.getTime());
                    } else {
                        OscarError err = OscarError.fromResponse(response);
                        L.w("LUtils.run error dropping packages - " + err);
                    }
                } catch (IOException ex) {
                    L.w("LUtils.run - Failed to upload location because " + ex.getLocalizedMessage());
                }
            }

            // now remove any older locations that have piled up
            Iterator<Location> iter = locationsQueue.iterator();
            while (iter.hasNext()) {
                Location l = iter.next();
                if (l.getTime() <= lastLocationMessage.time) {
                    iter.remove();
                }
            }
        }
    }

    @WorkerThread
    public static void upload(@NonNull Location location) {
        locationsQueue.add(location);
    }
}
