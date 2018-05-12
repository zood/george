package io.pijun.george;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.firebase.crash.FirebaseCrash;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.service.ActivityTransitionHandler;
import retrofit2.Response;

public class LocationUpdateRequestHandler {

    public static final String COMMAND_SHUT_DOWN = "shut_down";
    private static final String COMMAND_UPLOAD_LOCATION = "upload_location";

    private static int HANDLER_COUNT = 1;
    private static final int MAX_WAIT_SECONDS = 30;
    // The lock stays longer, because we need to give ourselves ample time to clean up
    private static final int LOCK_SECONDS = MAX_WAIT_SECONDS + 35;

    @Nullable private FusedLocationProviderClient client;
    private final Context context;
    private final LinkedBlockingQueue<Location> locationsQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> cmdsQueue = new LinkedBlockingQueue<>();
    private HandlerThread thread;
    private PowerManager.WakeLock wakeLock;
    private Listener listener;
    // We put handlers in this map, so they don't get GC'ed
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static ConcurrentHashMap<LocationUpdateRequestHandler, Boolean> activeHandlers = new ConcurrentHashMap<>();

    @AnyThread
    public LocationUpdateRequestHandler(@NonNull Context context, @Nullable Listener l) {
        this.context = context;
        this.listener = l;
        start();
    }

    @AnyThread
    public void issueCommand(String cmd) {
        cmdsQueue.add(cmd);
    }

    private void notifyListener() {
        if (listener != null) {
            try {
                listener.locationUpdateRequestHandlerFinished();
            } catch (Throwable t) {
                FirebaseCrash.report(t);
            }
        }
    }

    @WorkerThread
    private void run() {
        L.i("LURH.run()");
        activeHandlers.put(this, true);
        try {
            String cmd;
            while (true) {
                try {
                    cmd = cmdsQueue.take();
                } catch (InterruptedException ex) {
                    L.e("Error taking command", ex);
                    FirebaseCrash.report(ex);
                    continue;
                }

                switch (cmd) {
                    case COMMAND_SHUT_DOWN:
                        L.i("cmd - shut down");
                        shutDown();
                        return;
                    case COMMAND_UPLOAD_LOCATION:
                        L.i("cmd - upload location");
                        uploadLatestLocation();
                        break;
                    default:
                        L.w("unknown command: " + cmd);
                }
            }
        } catch (Throwable t) {
            FirebaseCrash.report(t);
        } finally {
            activeHandlers.remove(this);
        }
    }

    @WorkerThread
    private void shutDown() {
        if (client != null) {
            client.removeLocationUpdates(mLocationCallbackHelper);
        }
        notifyListener();
        try {
            wakeLock.release();
        } catch (Throwable ignore) {}
        try {
            thread.quitSafely();
        } catch (Throwable ignore) {}
    }

    @AnyThread
    private void start() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            notifyListener();
            return;
        }

        client = LocationServices.getFusedLocationProviderClient(context);
        LocationRequest request = LocationRequest.create();
        request.setInterval(DateUtils.SECOND_IN_MILLIS);
        request.setFastestInterval(DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        thread = new HandlerThread("LocationUpdateRequestHandler_" + HANDLER_COUNT++);
        thread.start();
        Task<Void> task = client.requestLocationUpdates(request, mLocationCallbackHelper, thread.getLooper());
        if (!task.isSuccessful()) {
            Exception ex = task.getException();
            if (ex == null) {
                FirebaseCrash.report(new RuntimeException("Unable to request location updates"));
            } else {
                FirebaseCrash.report(new RuntimeException("Unable to request location updates because of exception", ex));
            }
        }

        PowerManager pwrMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pwrMgr != null) {
            wakeLock = pwrMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationUpdateRequestHandlerLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(LOCK_SECONDS * DateUtils.SECOND_IN_MILLIS);
        }

        // start the run loop
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                LocationUpdateRequestHandler.this.run();
            }
        });

        // schedule a shutdown in case we can't get a precise location in a reasonable time
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(MAX_WAIT_SECONDS * DateUtils.SECOND_IN_MILLIS);
                } catch (InterruptedException ignore) {}
                L.i("LURH timeout");
                issueCommand(COMMAND_SHUT_DOWN);
            }
        });
    }

    @WorkerThread
    private void uploadLatestLocation() {
        LinkedList<Location> locations = new LinkedList<>();
        locationsQueue.drainTo(locations);
        if (locations.size() == 0) {
            return;
        }
        Location l = locations.getLast();

        Prefs prefs = Prefs.get(context);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("LURH.flush: token or keypair was null, so skipping upload");
            return;
        }

        UserComm locMsg = UserComm.newLocationInfo(l, ActivityTransitionHandler.getCurrentMovement());
        byte[] msgBytes = locMsg.toJSON();
        // share to our friends
        ArrayList<FriendRecord> friends = DB.get(context).getFriendsToShareWith();
        HashMap<String, EncryptedData> pkgs = new HashMap<>(friends.size());
        for (FriendRecord f : friends) {
            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, f.user.publicKey, keyPair.secretKey);
            if (encMsg == null) {
                L.w("LURH encryption failed for " + f.user.username);
                continue;
            }
            pkgs.put(Hex.toHexString(f.sendingBoxId), encMsg);
        }
        if (pkgs.size() > 0) {
            OscarAPI api = OscarClient.newInstance(token);
            try {
                Response<Void> response = api.dropMultiplePackages(pkgs).execute();
                if (!response.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(response);
                    L.i("LURH error uploading location: " + err);
                }
            } catch (IOException ex) {
                L.w("LURH network problem uploading location from LURH", ex);
            }
        }
    }

    private LocationCallback mLocationCallbackHelper = new LocationCallback() {

        @Override
        @WorkerThread
        public void onLocationResult(LocationResult result) {
            L.i("LURH.onLocationResult");
            Location location = result.getLastLocation();
            try {
                if (location != null) {
                    locationsQueue.add(location);
                    issueCommand(COMMAND_UPLOAD_LOCATION);
                    // if we get a location with an accuracy within 10 meters, that's good enough
                    // Also, the location needs to be from within the last 30 seconds
                    L.i("\thasAcc? " + location.hasAccuracy() + ", acc? " + location.getAccuracy() + ", time: " + location.getTime() + ", now: " + System.currentTimeMillis());
                    if (location.hasAccuracy() && location.getAccuracy() <= 10 &&
                            (System.currentTimeMillis() - location.getTime()) < 30000) {
                        issueCommand(COMMAND_SHUT_DOWN);
                    }
                }
            } catch (Throwable t) {
                L.w("Exception in onLocationResult", t);
                FirebaseCrash.report(t);
            }
        }
    };

    public interface Listener {
        @AnyThread
        void locationUpdateRequestHandlerFinished();
    }
}
