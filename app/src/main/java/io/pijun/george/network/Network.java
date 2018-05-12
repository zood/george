package io.pijun.george.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.annotation.NonNull;

import io.pijun.george.L;

public class Network {

    public static boolean isConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            // Should never happen
            L.w("isConnected - ConnectivityManager was null");
            return false;
        }
        NetworkInfo net = cm.getActiveNetworkInfo();
        return (net != null && net.isConnectedOrConnecting());
    }

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
