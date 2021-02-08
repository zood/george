package io.pijun.george.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import io.pijun.george.App;
import io.pijun.george.CloudLogger;
import io.pijun.george.Constants;
import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.Prefs;
import io.pijun.george.Sodium;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import xyz.zood.george.MainActivity;
import xyz.zood.george.Permissions;
import xyz.zood.george.R;

public class TimedShareService extends Service {

    private static final String ARG_SERVICE_ACTION = "service_action";
    private static final int NOTIFICATION_ID = 22;  // arbitrary number
    public static final String ACTION_START = "start";
    public static final String ACTION_COPY_LINK = "copy_link";
    public static final String TIMED_SHARE_CHANNEL_ID = "limited_share_01";
    /**
     * IsRunning is checked by LocationUtils. In the event that the process is cleaned
     * up without notice, the record of this limited share would still exist in the database
     * when the app comes back, and location data will continue to be shared with the limited share.
     * That's bad, so LocationUtils checks this to see if the service is actually running,
     * and if it's not, it will wipe the limited share data from the db.
     */
    public static volatile boolean IsRunning = false;
    private long startTime = 0;
    private long shareDuration = 0;

    private boolean isStarted = false;
    private KeyPair mKeyPair;
    private byte[] mSendingBoxId;
    private FusedLocationProviderClient mLocationProviderClient;
    private static final CopyOnWriteArrayList<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();
    private Future<?> stopTask;

    private static final Looper sServiceLooper;
    private static final Handler sServiceHandler;
    static {
        HandlerThread thread = new HandlerThread("TimedShareService");
        thread.start();

        sServiceLooper = thread.getLooper();
        sServiceHandler = new Handler(sServiceLooper);
    }

    /*
    Service binding is an unreliable mess. Just make this a global. It greatly
    simplifies the ViewModel and TimedShareFragment.
     */
    private static volatile TimedShareService singleton = null;

    @Nullable
    @AnyThread
    public static TimedShareService get() {
        return singleton;
    }

    @AnyThread
    private static boolean isValidServiceAction(String action) {
        if (action == null) {
            return false;
        }

        switch (action) {
            case ACTION_START:
            case ACTION_COPY_LINK:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    @AnyThread
    public static Intent newIntent(@NonNull Context context, String action) {
        if (!isValidServiceAction(action)) {
            throw new IllegalArgumentException("Invalid service action");
        }
        Intent i = new Intent(context, TimedShareService.class);
        i.putExtra(ARG_SERVICE_ACTION, action);

        return i;
    }

    @Nullable
    @Override
    @UiThread
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        singleton = this;
    }

    @Override
    @UiThread
    public void onDestroy() {
        if (isStarted) {
            shutdown();
        }
        TimedShareService.singleton = null;

        super.onDestroy();
    }

    @Override
    @UiThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getStringExtra(ARG_SERVICE_ACTION);
        if (action == null) {
            throw new IllegalArgumentException("You must provide a service action");
        }

        if (!isStarted) {
            isStarted = true;
            sServiceHandler.post(new WorkerRunnable() {
                @Override
                public void run() {
                    startLimitedShare();
                }
            });
        }

        return START_NOT_STICKY;
    }

    @AnyThread
    public long getShareDuration() {
        return shareDuration;
    }

    @AnyThread
    public long getStartTime() {
        return startTime;
    }

    @AnyThread
    @NonNull
    public String getUrl() {
        // k = the secret key created for this limited share
        // b = the drop box id locations will be dropped into/picked up from
        // i = id of the user sharing their location
        // u = username of the sharing user. not cryptographically required, just sent for presentation purposes
        // https://locationshare.zood.xyz/#k=<hex>&b=<hex>&i=<hex>&u=<username>
        Prefs prefs = Prefs.get(this);
        byte[] id = prefs.getUserId();
        if (id == null) {
            throw new RuntimeException("How is the user id null?");
        }
        String username = prefs.getUsername();
        if (username == null) {
            throw new RuntimeException("How is the username null?");
        }
        return String.format("https://locationshare.zood.xyz/#u=%s&k=%s&b=%s&i=%s",
                username,
                Hex.toHexString(mKeyPair.secretKey),
                Hex.toHexString(mSendingBoxId),
                Hex.toHexString(id));
    }

    @AnyThread
    public boolean isRunning() {
        return isStarted;
    }

    private void scheduleStopTask() {
        // Copy the task into another pointer, to avoid the race conditoin
        // with the null check.
        Future<?> task = stopTask;
        if (task != null) {
            task.cancel(true);
        }
        stopTask = null;
        stopTask = App.runOnUiThreadCancellable(shareDuration, new UiRunnable() {
            @Override
            public void run() {
                stopTask = null;
                stopSelf();
            }
        });
    }

    @AnyThread
    private void showNotification() {
        // if we're on Android O, we need to create the notification channel
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (mgr != null) {
                String name = getString(R.string.timed_share);
                NotificationChannel channel = new NotificationChannel(TIMED_SHARE_CHANNEL_ID,
                        name,
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Used only for the location broadcast notification.");
                mgr.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder bldr = new NotificationCompat.Builder(this, TIMED_SHARE_CHANNEL_ID);
        bldr.setSmallIcon(R.drawable.ic_timed_share);
        bldr.setContentTitle("Timed share is running");
        bldr.setPriority(NotificationCompat.PRIORITY_MIN);
        int reqCode = (int) (System.currentTimeMillis() % 10000);
        PendingIntent openIntent = PendingIntent.getActivity(this,
                reqCode,
                MainActivity.newIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT);
        bldr.setContentIntent(openIntent);

        startForeground(NOTIFICATION_ID, bldr.build());
    }

    @WorkerThread
    private void startLimitedShare() {
        if (!Permissions.checkForegroundLocationPermission(this)) {
            Toast.makeText(this, R.string.need_background_location_permission, Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        startTime = System.currentTimeMillis();
        // 30 minutes + 30 seconds. The 30 seconds is so the countdown timer shows
        // 30m for at least a little while. It looks weird to start with '29m'.
        shareDuration = 30 * 60 * 1000 + (30 * 1000);
//        shareDuration = 16000;
        App.isLimitedShareRunning = true;
        showNotification();
        // create a user
        mKeyPair = new KeyPair();
        int result = Sodium.generateKeyPair(mKeyPair);
        if (result != 0) {
            CloudLogger.log("TimedShareService failed to generate a keypair - result: " + result);
            return;
        }
        mSendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(mSendingBoxId);

        // add the user to the db for LocationUploadService to use
        try {
            DB.get().addLimitedShare(mKeyPair.publicKey, mSendingBoxId);
        } catch (DB.DBException dbe) {
            L.e("Unable to add limited share to db", dbe);
            CloudLogger.log(dbe);
            return;
        }

        TimedShareService.IsRunning = true;
        isStarted = true;

        notifyShareStarted(startTime, shareDuration);

        mLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest request = LocationRequest.create();
        request.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationProviderClient.requestLocationUpdates(request, mLocationCallbackHelper, sServiceLooper);

        // schedule a runnable to shut us down
        scheduleStopTask();
    }

    @UiThread
    private void shutdown() {
        isStarted = false;
        if (stopTask != null) {
            stopTask.cancel(true);
            stopTask = null;
        }

        startTime = 0;
        shareDuration = 0;
        App.isLimitedShareRunning = false;
        stopForeground(true);

        mLocationProviderClient.removeLocationUpdates(mLocationCallbackHelper);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                DB.get().deleteLimitedShares();
            }
        });

        TimedShareService.IsRunning = false;
        notifyShareFinished();
    }

    /**
     * Change the duration of the timed share.
     * @param duration The new duration.
     */
    @UiThread
    public void updateDuration(long duration) {
        if (!isStarted) {
            return;
        }

        if (duration <= 0) {
            stopSelf();
            return;
        }
        shareDuration = duration;
        notifyDurationChanged(shareDuration);
        scheduleStopTask();
    }

    private final LocationCallback mLocationCallbackHelper = new LocationCallback() {
        @Override
        @WorkerThread
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null) {
                return;
            }

            LocationUtils.upload(location);
        }
    };

    //region Listener

    public interface Listener {
        @WorkerThread default void onTimedShareStarted(long startTime, long duration) {}
        @WorkerThread default void onTimedShareFinished() {}
        @WorkerThread default void onTimedShareDurationChanged(long duration) {}
    }

    @AnyThread
    public static void addListener(@NonNull Listener listener) {
        WeakReference<Listener> ref = new WeakReference<>(listener);
        listeners.add(ref);
    }

    private void notifyDurationChanged(long duration) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onTimedShareDurationChanged(duration);
                }
            }
        });
    }

    @AnyThread
    private void notifyShareFinished() {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onTimedShareFinished();
                }
            }
        });
    }

    @AnyThread
    private void notifyShareStarted(long startTime, long duration) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onTimedShareStarted(startTime, duration);
                }
            }
        });
    }

    @AnyThread
    public static void removeListener(@NonNull Listener listener) {
        int i=0;
        while (i<listeners.size()) {
            WeakReference<Listener> ref = listeners.get(i);
            Listener l = ref.get();
            if (l == null || l == listener) {
                listeners.remove(i);
                continue;
            }
            i++;
        }
    }

    //endregion
}
