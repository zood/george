package xyz.zood.george;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class Permissions {

    // Ugly method name is because https://stackoverflow.com/a/36193309/211180
    @AnyThread
    public static boolean checkGrantedForegroundLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // Ugly method name is because https://stackoverflow.com/a/36193309/211180
    @AnyThread
    public static boolean checkGrantedBackgroundLocationPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /*
     * Returns an array of Manifest.permission strings that can be passed to
     * Fragment.requestPermissions(). The array's contents will depend on the API level of the
     * device.
     */
    @NonNull @AnyThread
    public static String[] getLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
        } else {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }
    }

}
