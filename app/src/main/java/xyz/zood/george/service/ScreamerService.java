package xyz.zood.george.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.text.format.DateUtils;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.R;
import io.pijun.george.WorkerRunnable;
import xyz.zood.george.receiver.ScreenOnReceiver;

public class ScreamerService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private static final String SCREAMER_CHANNEL_ID = "scream_01";
    private static final int NOTIFICATION_ID = 92;
    private static final String ACTION_ARG = "action";
    private static final String REMOVE_NOTIFICATION_ARG = "remove_notification";

    private MediaPlayer player;
    private boolean waitingForFocus;
    private ScreenOnReceiver screenOnReceiver;

    public enum Action {
        Start,
        Stop
    }

    private static AudioAttributes getAudioAttributes() {
        return new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
    }

    public static Intent newStartIntent(@NonNull Context context) {
        Intent i = new Intent(context, ScreamerService.class);
        i.putExtra(ACTION_ARG, Action.Start.name());
        return i;
    }

    public static Intent newStopIntent(@NonNull Context context, boolean removeNotification) {
        Intent i = new Intent(context, ScreamerService.class);
        i.putExtra(ACTION_ARG, Action.Stop.name());
        i.putExtra(REMOVE_NOTIFICATION_ARG, removeNotification);
        return i;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    @UiThread
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            throw new RuntimeException("You need to call newIntent to instantiate this service");
        }
        String action = extras.getString(ACTION_ARG);
        if (TextUtils.isEmpty(action)) {
            throw new RuntimeException("How come newIntent didn't add an action to the intent?");
        }
        if (action.equals(Action.Start.name())) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    scream();
                }
            });
        } else if (action.equals(Action.Stop.name())) {
            boolean removeNotification = extras.getBoolean(REMOVE_NOTIFICATION_ARG);
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    stopScreaming(removeNotification);
                }
            });
        }

        return START_NOT_STICKY;
    }

    @WorkerThread
    private void scream() {
        showNotification();

        // set the volume to 100
        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (mgr == null) {
            L.w("How is there no AudioManager?");
            return;
        }

        AudioAttributes audioAttr = getAudioAttributes();
        int reqResult;
        if (Build.VERSION.SDK_INT >= 26) {
            mgr.setStreamVolume(audioAttr.getVolumeControlStream(), 100, 0);
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttr)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            reqResult = mgr.requestAudioFocus(req);
        } else {
            mgr.setStreamVolume(AudioManager.STREAM_MUSIC, 100, 0);
            reqResult = mgr.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        switch (reqResult) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                L.w("Audio focus request failed");
                stopScreaming(true);
                return;
            case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                L.w("Audio focus request delayed");
                waitingForFocus = true;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
            default:
                L.i("Audio focus request granted");
        }

        // start playing the ringtone
        player = new MediaPlayer();
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (soundUri == null) {
                L.w("Default ringtone not found");
                stopScreaming(true);
                return;
            }
            player.setDataSource(this, soundUri);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setLooping(true);
            player.prepare();
        } catch (IOException ex) {
            L.w(ex.getLocalizedMessage(), ex);
            return;
        }

        player.start();

        // listen for SCREEN_ON
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenOnReceiver = new ScreenOnReceiver();
        registerReceiver(screenOnReceiver, filter);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5 * DateUtils.MINUTE_IN_MILLIS);
                    stopScreaming(false);
                } catch (InterruptedException ignore) {}
                if (player != null && player.isPlaying()) {
                    player.stop();
                }
            }
        });
    }

    private void showNotification() {
        // if we're on Android O, we need to create the notification channel
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (mgr != null) {
                String name = getString(R.string.find_my_phone);
                NotificationChannel channel = new NotificationChannel(
                        SCREAMER_CHANNEL_ID,
                        name,
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(getString(R.string.screamer_channel_description_msg));
                mgr.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder bldr = new NotificationCompat.Builder(this, SCREAMER_CHANNEL_ID);
        bldr.setSmallIcon(R.mipmap.ic_launcher);
        bldr.setContentTitle(getString(R.string.screamer_notification_msg));
        int reqCode = (int) (System.currentTimeMillis() % 1000);
        PendingIntent stopIntent = PendingIntent.getService(
                this,
                reqCode,
                ScreamerService.newStopIntent(this, true),
                PendingIntent.FLAG_UPDATE_CURRENT);
        bldr.addAction(R.drawable.ic_clear_black_24px, getString(R.string.dismiss), stopIntent);
        startForeground(NOTIFICATION_ID, bldr.build());
    }

    private void stopScreaming(boolean removeNotification) {
        if (screenOnReceiver != null) {
            unregisterReceiver(screenOnReceiver);
            screenOnReceiver = null;
        }

        if (player != null && player.isPlaying()) {
            player.stop();
            player.release();
            player = null;
        }

        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (mgr == null) {
            L.w("How is there no AudioManager?");
            return;
        }

        if (Build.VERSION.SDK_INT >= 26) {
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(getAudioAttributes())
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            mgr.abandonAudioFocusRequest(req);
        } else {
            mgr.abandonAudioFocus(this);
        }

        stopForeground(removeNotification);
    }

    //region OnAudioFocusChangeListener

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (waitingForFocus && focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            waitingForFocus = false;
            return;
        }

        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            stopScreaming(false);
        }
    }

    //endregion
}
