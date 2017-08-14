package io.pijun.george.service;

import android.app.Service;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import io.pijun.george.App;
import io.pijun.george.DB;
import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Sodium;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.MovementsUpdated;
import io.pijun.george.event.UserLoggedOut;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.LimitedShare;
import io.pijun.george.models.MovementType;

public class LocationUploadService extends Service {

    @AnyThread
    @NonNull
    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, LocationUploadService.class);
    }

    private static Handler sServiceHandler;
    static {
        HandlerThread thread = new HandlerThread(LocationUploadService.class.getSimpleName());
        thread.start();

        Looper looper = thread.getLooper();
        sServiceHandler = new Handler(looper);
    }

    private LinkedBlockingQueue<Location> mLocations = new LinkedBlockingQueue<>();
    private ArrayList<MovementType> mMovements = new ArrayList<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    @UiThread
    public void onCreate() {
        L.i("LUS.onCreate");
        super.onCreate();

        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(LocationJobService.getJobInfo(this));

        App.registerOnBus(this);
    }

    @Override
    @UiThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    @UiThread
    public void onDestroy() {
        L.i("LUS.onDestroy");
        App.unregisterFromBus(this);

        super.onDestroy();
    }

    @Subscribe
    @Keep
    public void onLocationChanged(final Location l) {
        sServiceHandler.post((WorkerRunnable) () -> {
            // check if this is a duplicate location
            if (!mLocations.isEmpty() && mLocations.peek().getElapsedRealtimeNanos() == l.getElapsedRealtimeNanos()) {
                return;
            }

            mLocations.add(l);
            flush();
        });
    }

    @Subscribe
    @Keep
    public void onMovementsUpdated(MovementsUpdated mu) {
        this.mMovements = mu.movements;
    }

    @Subscribe
    @Keep
    public void onUserLoggedOut(UserLoggedOut evt) {
        stopSelf();
    }

    /**
     * Get the most recent location and report it.
     */
    @WorkerThread
    private void flush() {
        // If we have no location to report, just get out of here.
        if (mLocations.isEmpty()) {
            L.i("LUS.flush: no location to flush");
            return;
        }

        Prefs prefs = Prefs.get(this);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("LUS.flush: token or keypair was null, so skipping upload");
            mLocations.clear();
            return;
        }

        Location location = mLocations.peek();
        UserComm locMsg = UserComm.newLocationInfo(location, mMovements);
        byte[] msgBytes = locMsg.toJSON();
        // share to our friends
        ArrayList<FriendRecord> friends = DB.get(this).getFriendsToShareWith();
        boolean droppedPackage = false;
        for (FriendRecord fr : friends) {
            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, fr.user.publicKey, keyPair.secretKey);
            if (encMsg == null) {
                L.w("LUS.flush: encryption failed");
                continue;
            }
            OscarClient.queueDropPackage(this, token, Hex.toHexString(fr.sendingBoxId), encMsg);
            droppedPackage = true;
        }
        if (droppedPackage) {
            Prefs.get(this).setLastLocationUpdateTime(System.currentTimeMillis());
        }
        // also check for a limited share
        LimitedShare ls = DB.get(this).getLimitedShare();
        if (ls != null) {
            L.i("LUS.flush: to limited share");
            if (LimitedShareService.IsRunning) {
                EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, ls.publicKey, keyPair.secretKey);
                if (encMsg != null) {
                    OscarClient.queueDropPackage(this, token, Hex.toHexString(ls.sendingBoxId), encMsg);
                } else {
                    L.w("LUS.flush: encryption failed");
                }
            } else {
                L.i("LUS.flush: oops. the limited share isn't running. we'll delete it.");
                DB.get(this).deleteLimitedShares();
            }
        }

        mLocations.clear();
    }
}
