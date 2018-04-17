package io.pijun.george.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.support.annotation.NonNull;

import io.pijun.george.L;

public class Network {

    public static boolean isBackgroundDataRestricted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }

        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            L.w("ConnectivityManager was null!");
            return false;
        }

        int status = connMgr.getRestrictBackgroundStatus();
        return status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
    }

}
