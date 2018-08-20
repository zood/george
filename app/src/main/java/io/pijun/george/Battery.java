package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;

public class Battery {

    private static long lastCheckTime = 0;
    private static int lastLevel = -1;

    @CheckResult
    @IntRange(from=-1, to=100)
    public static int getLevel(@NonNull Context context) {
        long now = System.currentTimeMillis();
        long since = now - lastCheckTime;
        if (since > 3 * DateUtils.MINUTE_IN_MILLIS) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent status = context.registerReceiver(null, filter);
            if (status != null) {
                Bundle extras = status.getExtras();
                if (extras != null) {
                    lastCheckTime = now;
                    lastLevel = extras.getInt(BatteryManager.EXTRA_LEVEL, -1);
                }
            }
        }

        return lastLevel;
    }

}
