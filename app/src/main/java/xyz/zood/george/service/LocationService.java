package xyz.zood.george.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.concurrent.Future;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.Prefs;
import io.pijun.george.UiRunnable;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.UserRecord;
import xyz.zood.george.Permissions;
import xyz.zood.george.R;

public class LocationService extends Service {

    private static final String FINDING_LOCATION_CHANNEL_ID = "finding_location_01";
    private static final int NOTIFICATION_ID = 44;  // arbitrary number
    private static final String REQUESTING_USER_ID = "requesting_user_id";

    private FusedLocationProviderClient fusedLocationClient;
    private Future<?> timeout;
    private long requestingUserId = -1;

    @Override
    public void onCreate() {
        L.i("LocationService.onCreate");
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        timeout = App.runOnUiThreadCancellable(30 * DateUtils.SECOND_IN_MILLIS, new UiRunnable() {
            @Override
            public void run() {
                L.i("LocationService.timer expired");
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        L.i("LocationService.onStartCommand");
        requestingUserId = intent.getLongExtra(REQUESTING_USER_ID, -1);
        startForegroundService();
        startLocationUpdates();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        L.i("LocationService.onDestroy");
        super.onDestroy();
        stopLocationUpdates();
        Future<?> t = timeout;
        if (t != null) {
            t.cancel(false);
            timeout = null;
        }
        if (requestingUserId != -1) {
            Prefs prefs = Prefs.get(this);
            String token = prefs.getAccessToken();
            KeyPair kp = prefs.getKeyPair();
            UserRecord user = DB.get().getUser(requestingUserId);
            if (token != null && kp != null && user != null) {
                UserComm finished = UserComm.newLocationUpdateRequestReceived(UserComm.LOCATION_UPDATE_REQUEST_ACTION_FINISHED);
                String errMsg = OscarClient.queueSendMessage(OscarClient.getQueue(getApplicationContext()), user, kp, token, finished.toJSON(), true, true);
                if (errMsg != null) {
                    L.w("LocationService errorNotifyingRequestingUser: " + errMsg);
                }
            }
        }
    }

    public static Intent newIntent(Context context, long userId) {
        Intent intent = new Intent(context, LocationService.class);
        intent.putExtra(REQUESTING_USER_ID, userId);
        return intent;
    }

    private void startForegroundService() {
        L.i("LocationService.startForegroundService");
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, FINDING_LOCATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.finding_your_location))
                .setSmallIcon(R.drawable.ic_position_service)
                .build();
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    }

    private void createNotificationChannel() {
        String name = getString(R.string.finding_location);
        NotificationChannel channel = new NotificationChannel(
                FINDING_LOCATION_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_MIN);
        channel.setDescription("Used when trying to find your location.");

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(DateUtils.SECOND_IN_MILLIS).
                setPriority(Priority.PRIORITY_HIGH_ACCURACY).
                build();

        if (!Permissions.checkBackgroundLocationPermission(this)) {
            stopSelf();
            return;
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(callback);
    }

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            Location loc = locationResult.getLastLocation();
            if (loc == null) {
                return;
            }

            LocationUtils.upload(loc);

            // if we get a location with an accuracy of <= 10 meters, that's good enough.
            boolean isAccurate = loc.hasAccuracy() && loc.getAccuracy() <= 10;
            // Also check that it's a recent value, and not some cached value the system gave us.
            boolean isRecent = (System.currentTimeMillis() - loc.getTime()) < 30 * DateUtils.SECOND_IN_MILLIS;
            if (isAccurate && isRecent) {
                L.i("LocationService - stopping");
                stopSelf();
            }
        }
    };
}
