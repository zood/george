package xyz.zood.george;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;


// Ugly method names because https://stackoverflow.com/a/36193309/211180
public class Permissions {

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread @CheckResult
    public static boolean checkBackgroundLocationPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkGrantedBackgroundLocationPermission(ctx);
        } else {
            return checkGrantedPreQLocationPermission(ctx);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread @CheckResult
    public static boolean checkForegroundLocationPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkGrantedForegroundLocationPermission(ctx);
        } else {
            return checkGrantedPreQLocationPermission(ctx);
        }
    }

    @AnyThread @RequiresApi(api = Build.VERSION_CODES.Q) @CheckResult
    public static boolean checkGrantedActivityRecognitionPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @AnyThread @RequiresApi(api = Build.VERSION_CODES.Q) @CheckResult
    public static boolean checkGrantedBackgroundLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @AnyThread @RequiresApi(api = Build.VERSION_CODES.Q) @CheckResult
    public static boolean checkGrantedForegroundLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @AnyThread @CheckResult
    public static boolean checkGrantedPreQLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @NonNull @AnyThread @CheckResult
    public static String[] getActivityRecognitionPermissions() {
        return new String[]{Manifest.permission.ACTIVITY_RECOGNITION};
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @NonNull @AnyThread @CheckResult
    public static String[] getBackgroundLocationPermissions() {
        return new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @NonNull @AnyThread @CheckResult
    public static String[] getForegroundLocationPermissions() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    @NonNull @AnyThread @CheckResult
    public static String[] getPreQLocationPermissions() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }
}
