package io.pijun.george.network;

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;

import io.pijun.george.L;

public class Network {

    /**
     * Determine whether background data usage is restricted for this app.
     * @param context A Context object
     * @return true if background data is restricted. false, otherwise.
     */
    public static boolean isBackgroundDataRestricted(@NonNull Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            L.w("ConnectivityManager was null!");
            return false;
        }

        int status = connMgr.getRestrictBackgroundStatus();
        return status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
    }

}
