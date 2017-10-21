package io.pijun.george.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.pijun.george.L;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        L.i("BootReceiver.onReceive");
    }
}
