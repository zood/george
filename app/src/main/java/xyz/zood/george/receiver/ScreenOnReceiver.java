package xyz.zood.george.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import io.pijun.george.L;
import xyz.zood.george.service.ScreamerService;

public class ScreenOnReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("ScreenOnReceiver.onReceive");

        Intent i = ScreamerService.newStopIntent(context, false);
        ContextCompat.startForegroundService(context, i);
    }

}
