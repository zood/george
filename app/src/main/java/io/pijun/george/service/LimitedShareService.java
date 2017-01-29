package io.pijun.george.service;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.NotificationCompat;
import android.text.format.DateUtils;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.crash.FirebaseCrash;

import java.security.SecureRandom;

import io.pijun.george.App;
import io.pijun.george.Constants;
import io.pijun.george.DB;
import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.R;
import io.pijun.george.Sodium;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.crypto.KeyPair;

public class LimitedShareService extends Service implements LocationListener {

    private static final String ARG_SERVICE_ACTION = "service_action";
    private static final int NOTIFICATION_ID = 22;  // arbitrary number
    public static final String ACTION_START = "start";
    public static final String ACTION_END = "end";
    public static final String ACTION_COPY_LINK = "copy_link";
    /**
     * IsRunning is checked by LocationUploadService. In the event that the process is cleaned
     * up without notice, the record of this limited share will still exist in the database
     * when the app comes back, and location data will continue to be shared with the limited share.
     * That's bad, so LocationUploadService checks this to see if the service is actually running,
     * and if it's not, it will wipe the limited share data from the db.
     */
    public static volatile boolean IsRunning = false;

    private boolean mIsStarted = false;
    private KeyPair mKeyPair;
    private byte[] mSendingBoxId;
    private GoogleApiClient mGoogleClient;

    private static Looper sServiceLooper;
    private static Handler sServiceHandler;
    static {
        HandlerThread thread = new HandlerThread(LocationListenerService.class.getSimpleName());
        thread.start();

        sServiceLooper = thread.getLooper();
        sServiceHandler = new Handler(sServiceLooper);
    }

    @AnyThread
    private static boolean isValidServiceAction(String action) {
        if (action == null) {
            return false;
        }

        switch (action) {
            case ACTION_START:
            case ACTION_END:
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
        Intent i = new Intent(context, LimitedShareService.class);
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
    @UiThread
    public void onDestroy() {
        L.i("LSS.onDestroy");
        IsRunning = false;
        stopLimitedShare(false);
    }

    @Override
    @UiThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        L.i("LSS.onStartCommand {Intent: " + intent + ", extras:" + intent.getExtras() + ", flags: " + flags + ", startId: " + startId + "}");
        String action = intent.getStringExtra(ARG_SERVICE_ACTION);
        if (action == null) {
            throw new IllegalArgumentException("You must provide a service action");
        }

        switch (action) {
            case ACTION_START:
                if (!mIsStarted) {
                    mIsStarted = true;
                    sServiceHandler.post(new WorkerRunnable() {
                        @Override
                        public void run() {
                            startLimitedShare();
                        }
                    });
                }
                break;
            case ACTION_COPY_LINK:
                shareLink();
                break;
            case ACTION_END:
                stopLimitedShare(true);
                break;
        }
        L.i("  service_action: " + action);
        return START_NOT_STICKY;
    }

    @AnyThread
    @NonNull
    private String getUrl() {
        // k = the secret key created for this limited share
        // b = the drop box id locations will be dropped into/picked up from
        // i = id of the user sharing their location
        // u = username of the sharing user. not cryptographically required, just sent for presentation purposes
        // https://t.pijun.io/#k=<hex>&b=<hex>&i=<hex>&u=<username>
        Prefs prefs = Prefs.get(this);
        byte[] id = prefs.getUserId();
        if (id == null) {
            throw new RuntimeException("How is the user id null?");
        }
        String username = prefs.getUsername();
        if (username == null) {
            throw new RuntimeException("How is the username null?");
        }
        return String.format("https://t.pijun.io/#u=%s&k=%s&b=%s&i=%s",
                username,
                Hex.toHexString(mKeyPair.secretKey),
                Hex.toHexString(mSendingBoxId),
                Hex.toHexString(id));
    }

    @AnyThread
    private void showNotification() {
        NotificationCompat.Builder bldr = new NotificationCompat.Builder(this);
        bldr.setSmallIcon(R.mipmap.ic_launcher);
        bldr.setContentTitle("Location broadcast is on");
        bldr.setContentText("Share the link to let others view your location");
        int reqCode = (int) (System.currentTimeMillis() % 10000);
        PendingIntent shareIntent = PendingIntent.getService(
                this,
                reqCode,
                LimitedShareService.newIntent(this, LimitedShareService.ACTION_COPY_LINK),
                PendingIntent.FLAG_UPDATE_CURRENT);
        bldr.addAction(R.drawable.ic_content_copy_black_24px, "Share link", shareIntent);
        PendingIntent stopIntent = PendingIntent.getService(
                this,
                reqCode + 1,
                LimitedShareService.newIntent(this, LimitedShareService.ACTION_END),
                PendingIntent.FLAG_UPDATE_CURRENT);
        bldr.addAction(R.drawable.ic_clear_black_24px, "Stop broadcasting", stopIntent);

        startForeground(NOTIFICATION_ID, bldr.build());
    }

    @AnyThread
    private void shareLink() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TITLE, "Track location");
        sendIntent.putExtra(Intent.EXTRA_TEXT, getUrl());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Send to"));
    }

    @WorkerThread
    private void startLimitedShare() {
        showNotification();
        // create a user
        mKeyPair = new KeyPair();
        int result = Sodium.generateKeyPair(mKeyPair);
        if (result != 0) {
            L.w("Error generating a keypair for limited sharing: " + result);
            // TODO: handle this
            return;
        }
        mSendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(mSendingBoxId);

        // add the user to the db for LocationUploadService to use
        try {
            DB.get(this).addLimitedShare(mKeyPair.publicKey, mSendingBoxId);
        } catch (DB.DBException dbe) {
            L.e("Unable to add limited share to db", dbe);
            FirebaseCrash.report(dbe);
            // TODO: handle this
            return;
        }

        LimitedShareService.IsRunning = true;

        // start the fused location provider
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        ConnectionResult connectionResult = mGoogleClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            L.w("LSS was unable to connect to google api");
            L.w("  has resolution? " + connectionResult.hasResolution());
            // TODO: handle this
            return;
        }
        LocationRequest req = LocationRequest.create();
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        req.setInterval(30 * DateUtils.SECOND_IN_MILLIS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleClient, req, this, sServiceLooper);
        } else {
            // TODO: handle this
            return;
        }

        // copy the link to the user's clipboard
        String url = getUrl();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newRawUri("Pijun URL", Uri.parse(url));
        cm.setPrimaryClip(clipData);

        // create a toast to let the user know it's done
        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void stopLimitedShare(boolean userStopped) {
        stopForeground(true);

        if (mGoogleClient != null && mGoogleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
            mGoogleClient.disconnect();
            mGoogleClient = null;
        }

        DB.get(this).deleteLimitedShares();
        LimitedShareService.IsRunning = false;


        if (userStopped) {
            stopSelf();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            App.postOnBus(location);
        }
    }
}
