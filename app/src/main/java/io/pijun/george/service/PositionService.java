package io.pijun.george.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.pijun.george.App;
import io.pijun.george.CloudLogger;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.Prefs;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.UserRecord;
import xyz.zood.george.Permissions;
import xyz.zood.george.R;

public class PositionService extends Service {

    enum Command {
        ShutDown,
        UploadLocation
    }

    private static final String FINDING_LOCATION_CHANNEL_ID = "finding_location_01";
    private static final int NOTIFICATION_ID = 44;  // arbitrary number
    private static final String NOTIFY_USER_ID_KEY = "notify_user_id";

    private static int HANDLER_COUNT = 1;
    public static final int MAX_WAIT_SECONDS = 20;
    // The lock stays longer, because we need to give ourselves ample time to clean up
    private static final int LOCK_SECONDS = MAX_WAIT_SECONDS + 35;

    private final LinkedBlockingQueue<Location> locationsQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Command> cmdsQueue = new LinkedBlockingQueue<>();
    private HandlerThread thread;
    private PowerManager.WakeLock wakeLock;
    private long notifyUserId = -1;

    private static final Handler serviceHandler;
    static {
        HandlerThread thread = new HandlerThread("PositionService");
        thread.start();
        serviceHandler = new Handler(thread.getLooper());
    }
    private static CountDownLatch waitLatch = new CountDownLatch(1);

    public static void await() {
        // Still technically racey, but it's ok. We have a timeout.
        CountDownLatch localRef = waitLatch;
        if (localRef == null) {
            return;
        }
        try {
            localRef.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            L.w("PS.await interrupted", ie);
        }
    }

    private void issueCommand(Command cmd) {
        cmdsQueue.add(cmd);
    }

    public static Intent newIntent(@NonNull Context context, @Nullable Long notifyUserId) {
        Intent i = new Intent(context, PositionService.class);
        if (notifyUserId != null) {
            i.putExtra(NOTIFY_USER_ID_KEY, notifyUserId.longValue());
        }
        return i;
    }

    private void notifyWaiters() {
        // I know this is racey, but because all the waiting threads have a timeout it's not a big deal
        waitLatch.countDown();
        waitLatch = new CountDownLatch(1);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notifyUserId = intent.getLongExtra(NOTIFY_USER_ID_KEY, -1);

        serviceHandler.post(new WorkerRunnable() {
            @Override
            public void run() {
                start();
            }
        });
        return START_NOT_STICKY;
    }

    @WorkerThread
    private void run() {
        L.i("PS.run");
        try {
            Command cmd;
            while (true) {
                try {
                    cmd = cmdsQueue.take();
                } catch (InterruptedException ex) {
                    L.e("Error taking command", ex);
                    CloudLogger.log(ex);
                    continue;
                }

                switch (cmd) {
                    case ShutDown:
//                        L.i("cmd - shut down");
                        shutDown();
                        return;
                    case UploadLocation:
//                        L.i("cmd - upload location");
                        uploadLatestLocation();
                        break;
                    default:
                        L.w("unknown command: " + cmd);
                }
            }
        } catch (Throwable t) {
            CloudLogger.log(t);
        }
    }

    @WorkerThread
    private void sendDoneMessage() {
        if (notifyUserId == -1) {
            return;
        }
        Prefs prefs = Prefs.get(this);
        String token = prefs.getAccessToken();
        KeyPair kp = prefs.getKeyPair();
        if (token == null || kp == null) {
            L.w("PS.sendDoneMessage token or keypair was null");
            return;
        }
        UserRecord user = DB.get().getUser(notifyUserId);
        if (user == null) {
            L.w("PS.sendDoneMessage user record not found");
            return;
        }
        UserComm finished = UserComm.newLocationUpdateRequestReceived(UserComm.LOCATION_UPDATE_REQUEST_ACTION_FINISHED);
        L.i("PS.sendDoneMessage is sending");
        String errMsg = OscarClient.immediatelySendMessage(user, token, kp, finished, true, true);
        if (errMsg != null) {
            L.w("PS.sendDoneMessage - " + errMsg);
        }
    }

    @WorkerThread
    private void showNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (mgr != null) {
                String name = getString(R.string.finding_location);
                NotificationChannel channel = new NotificationChannel(
                        FINDING_LOCATION_CHANNEL_ID,
                        name,
                        NotificationManager.IMPORTANCE_MIN);
                channel.setDescription("Used when trying to find your location.");
                mgr.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder bldr = new NotificationCompat.Builder(this, FINDING_LOCATION_CHANNEL_ID);
        bldr.setSmallIcon(R.drawable.ic_position_service);
        bldr.setContentTitle(getString(R.string.finding_your_location));
        startForeground(NOTIFICATION_ID, bldr.build());
    }

    @WorkerThread
    private void shutDown() {
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
        client.removeLocationUpdates(callback);

        // Send a done message if we were given a user to notify
        sendDoneMessage();

        try { wakeLock.release(); }
        catch (Throwable ignore) {}

        try { thread.quitSafely(); }
        catch (Throwable ignore) {}

        stopSelf();

        notifyWaiters();
    }

    @WorkerThread
    private void start() {
        if (!Permissions.checkBackgroundLocationPermission(this)) {
            stopSelf();
            notifyWaiters();
            return;
        }

        showNotification();

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest request = LocationRequest.create();
        request.setInterval(DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        thread = new HandlerThread("PositionService_" + HANDLER_COUNT++);
        thread.start();
        client.requestLocationUpdates(request, callback, thread.getLooper());

        PowerManager pwrMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pwrMgr != null) {
            wakeLock = pwrMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZoodLocation:PositionServiceLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(LOCK_SECONDS * DateUtils.SECOND_IN_MILLIS);
        }

        // start the run loop
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                PositionService.this.run();
            }
        });

        // schedule a shutdown in case we can't get a precise location in a reasonable time
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(MAX_WAIT_SECONDS * DateUtils.SECOND_IN_MILLIS);
                } catch (InterruptedException ignore) {}
                L.i("PS timeout");
                issueCommand(Command.ShutDown);
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

        LocationUtils.upload(l);
    }

    private final LocationCallback callback = new LocationCallback() {
        @Override
        @WorkerThread
        public void onLocationResult(LocationResult result) {
            Location loc = result.getLastLocation();
            if (loc == null) {
                return;
            }
            try {
                locationsQueue.offer(loc);
                issueCommand(Command.UploadLocation);
                // if we get a location with an accuracy of <= 10 meters, that's good enough.
                boolean isAccurate = loc.hasAccuracy() && loc.getAccuracy() <= 10;
//                L.i("PS - acc: " + loc.getAccuracy() + ", time: " + loc.getTime() + ", now: " + System.currentTimeMillis());
                // Also check that it's a recent value, and not some cached value the system gave us.
                boolean isRecent = (System.currentTimeMillis() - loc.getTime()) < 30 * DateUtils.SECOND_IN_MILLIS;
                if (isAccurate && isRecent) {
                    issueCommand(Command.ShutDown);
                }
            } catch (Throwable t) {
                L.w("Exception in PS.onLocationResult");
                CloudLogger.log(t);
            }
        }
    };
}
