package io.pijun.george;

import android.content.Context;
import android.location.Location;
import android.support.annotation.AnyThread;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.LimitedShare;
import io.pijun.george.service.ActivityTransitionHandler;
import io.pijun.george.service.LimitedShareService;

public class LocationUploader {

    private volatile static LocationUploader sSingleton;
    private LinkedBlockingQueue<Location> mLocations = new LinkedBlockingQueue<>();

    private LocationUploader() {
    }

    /**
     * Get the most recent location and report it.
     */
    @WorkerThread
    private void flush() {
        // If we have no location to report, just get out of here.
        if (mLocations.isEmpty()) {
            L.i("LU.flush: no location to flush");
            return;
        }

        Context ctx = App.getApp();
        Prefs prefs = Prefs.get(ctx);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("LU.flush: token or keypair was null, so skipping upload");
            mLocations.clear();
            return;
        }

        Location location = mLocations.peek();
        if (location == null) {
            // another call to flush could have raced us to the last location
            return;
        }
        UserComm locMsg = UserComm.newLocationInfo(location, ActivityTransitionHandler.getCurrentMovement());
        byte[] msgBytes = locMsg.toJSON();
        // share to our friends
        ArrayList<FriendRecord> friends = DB.get(ctx).getFriendsToShareWith();
        HashMap<String, EncryptedData> pkgs = new HashMap<>(friends.size());
        for (FriendRecord f : friends) {
            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, f.user.publicKey, keyPair.secretKey);
            if (encMsg == null) {
                L.w("LU.flush encryption failed for " + f.user.username);
                continue;
            }
            pkgs.put(Hex.toHexString(f.sendingBoxId), encMsg);
        }
        LimitedShare ls = DB.get(ctx).getLimitedShare();
        if (ls != null) {
            L.i("LU.flush: to limited share");
            if (LimitedShareService.IsRunning) {
                EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, ls.publicKey, keyPair.secretKey);
                if (encMsg != null) {
                    pkgs.put(Hex.toHexString(ls.sendingBoxId), encMsg);
                } else {
                    L.w("LU.flush: limited share encryption failed");
                }
            } else {
                L.i("LU.flush: oops. the limited share isn't running. we'll delete it.");
                DB.get(ctx).deleteLimitedShares();
            }
        }
        if (pkgs.size() > 0) {
            OscarClient.queueDropMultiplePackages(ctx, token, pkgs);
            Prefs.get(ctx).setLastLocationUpdateTime(System.currentTimeMillis());
        }

        mLocations.clear();
    }

    @AnyThread
    @NonNull
    public static LocationUploader get() {
        if (sSingleton == null) {
            synchronized (LocationUploader.class) {
                if (sSingleton == null) {
                    sSingleton = new LocationUploader();
                }
            }
        }
        return sSingleton;
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationChanged(final Location l) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                // check if this is a duplicate location
                if (!mLocations.isEmpty() && mLocations.peek().getElapsedRealtimeNanos() == l.getElapsedRealtimeNanos()) {
                    L.i("\tduplicate location");
                    return;
                }

                mLocations.add(l);
                flush();
            }
        });
    }
}
