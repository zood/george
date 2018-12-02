package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

class Battery {

    static class State {
        int level = -1;
        boolean isCharging;

        private State() {
        }
    }

    @NonNull
    @CheckResult
    static State getState(Context context) {
        State state = new State();

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent status = context.registerReceiver(null, filter);
        if (status == null) {
            return state;
        }

        state.level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int chargingState = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        state.isCharging =  chargingState == BatteryManager.BATTERY_STATUS_CHARGING ||
                            chargingState == BatteryManager.BATTERY_STATUS_FULL;

        return state;
    }

}
