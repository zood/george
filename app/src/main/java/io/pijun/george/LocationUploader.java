package io.pijun.george;

import android.app.job.JobScheduler;
import android.content.Context;
import android.location.Location;
import android.support.annotation.AnyThread;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.google.firebase.crash.FirebaseCrash;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.MovementsUpdated;
import io.pijun.george.event.UserLoggedIn;
import io.pijun.george.event.UserLoggedOut;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.LimitedShare;
import io.pijun.george.models.MovementType;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.service.LocationJobService;

public class LocationUploader {

    private volatile static LocationUploader sSingleton;
    private LinkedBlockingQueue<Location> mLocations = new LinkedBlockingQueue<>();
    private ArrayList<MovementType> mMovements = new ArrayList<>();

    private LocationUploader() {
        // Put this on a background thread, because Pref.isLoggedIn() might hit the disk.
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                // check if the user is already logged in. If so, schedule the LocationJobService
                App app = App.getApp();
                if (Prefs.get(App.getApp()).isLoggedIn()) {
                    JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    if (scheduler == null) {
                        // should never happen
                        FirebaseCrash.log("JobScheduler was null");
                        return;
                    }
                    scheduler.schedule(LocationJobService.getJobInfo(app));
                }
            }
        });
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
        UserComm locMsg = UserComm.newLocationInfo(location, mMovements);
        byte[] msgBytes = locMsg.toJSON();
        // share to our friends
        ArrayList<FriendRecord> friends = DB.get(ctx).getFriendsToShareWith();
        boolean droppedPackage = false;
        for (FriendRecord fr : friends) {
            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, fr.user.publicKey, keyPair.secretKey);
            if (encMsg == null) {
                L.w("LU.flush: encryption failed");
                continue;
            }
            OscarClient.queueDropPackage(ctx, token, Hex.toHexString(fr.sendingBoxId), encMsg);
            droppedPackage = true;
        }
        if (droppedPackage) {
            Prefs.get(ctx).setLastLocationUpdateTime(System.currentTimeMillis());
        }
        // also check for a limited share
        LimitedShare ls = DB.get(ctx).getLimitedShare();
        if (ls != null) {
            L.i("LU.flush: to limited share");
            if (LimitedShareService.IsRunning) {
                EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, ls.publicKey, keyPair.secretKey);
                if (encMsg != null) {
                    OscarClient.queueDropPackage(ctx, token, Hex.toHexString(ls.sendingBoxId), encMsg);
                } else {
                    L.w("LU.flush: encryption failed");
                }
            } else {
                L.i("LU.flush: oops. the limited share isn't running. we'll delete it.");
                DB.get(ctx).deleteLimitedShares();
            }
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

    @Subscribe
    @Keep
    @UiThread
    public void onMovementsUpdated(MovementsUpdated mu) {
        this.mMovements = mu.movements;
    }

    @Subscribe
    @Keep
    @UiThread
    public void onUserLoggedIn(UserLoggedIn evt) {
        App app = App.getApp();
        JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            // should never happen
            FirebaseCrash.log("JobScheduler was null");
            return;
        }
        scheduler.schedule(LocationJobService.getJobInfo(app));
    }

    @Subscribe
    @Keep
    @UiThread
    public void onUserLoggedOut(UserLoggedOut evt) {
        App app = App.getApp();
        JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            // should never happen
            FirebaseCrash.log("JobScheduler was null");
            return;
        }
        scheduler.cancelAll();
    }
}
