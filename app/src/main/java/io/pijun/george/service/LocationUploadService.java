package io.pijun.george.service;

import android.app.Service;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
        L.i("LUS.onLocationChanged");
        sServiceHandler.post(new WorkerRunnable() {
            @Override
            public void run() {
                // check if this is a duplicate location
                if (!mLocations.isEmpty() && mLocations.peek().getElapsedRealtimeNanos() == l.getElapsedRealtimeNanos()) {
                    return;
                }

                mLocations.add(l);

                // make sure we have an internet connection before attempting to send
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                if (isConnected) {
                    flush();
                }
            }
        });
    }

    @Subscribe
    @Keep
    public void onMovementsUpdated(MovementsUpdated mu) {
        this.mMovements = mu.movements;
    }

    /**
     * Get the most recent location and report it.
     */
    @WorkerThread
    private void flush() {
        L.i("LUS.flush");
        // If we have no location to report, just get out of here.
        if (mLocations.isEmpty()) {
            L.i("  no location to flush");
            return;
        }

        Prefs prefs = Prefs.get(this);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("  LM.flush token or keypair was null, so skipping upload");
            mLocations.clear();
            return;
        }

        Location location = mLocations.peek();
        UserComm locMsg = UserComm.newLocationInfo(location, mMovements);
        byte[] msgBytes = locMsg.toJSON();
        // share to our friends
        ArrayList<FriendRecord> friends = DB.get(this).getFriendsToShareWith();
        for (FriendRecord fr : friends) {
//            L.i("  to friend " + fr.user.username);
            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, fr.user.publicKey, keyPair.secretKey);
            if (encMsg == null) {
                L.w("  encryption failed");
                continue;
            }
            OscarClient.queueDropPackage(this, token, Hex.toHexString(fr.sendingBoxId), encMsg);
        }
        // also check for a limited share
        LimitedShare ls = DB.get(this).getLimitedShare();
        if (ls != null) {
            L.i("  to limited share");
            if (LimitedShareService.IsRunning) {
                EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, ls.publicKey, keyPair.secretKey);
                if (encMsg != null) {
                    OscarClient.queueDropPackage(this, token, Hex.toHexString(ls.sendingBoxId), encMsg);
                } else {
                    L.w("  encryption failed");
                }
            } else {
                L.i("  oops. the limited share isn't running. we'll delete it.");
                DB.get(this).deleteLimitedShares();
            }
        }

        mLocations.clear();
    }
}
