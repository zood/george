package xyz.zood.george;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

// Ugly method names because https://stackoverflow.com/a/36193309/211180
public class Permissions {

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread @CheckResult
    public static boolean checkBackgroundLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread @CheckResult
    public static boolean checkForegroundLocationPermission(@NonNull Context ctx) {
        for (String perm : getForegroundLocationPermissions()) {
            if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    @AnyThread @CheckResult
    public static boolean checkActivityRecognitionPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull @AnyThread @CheckResult
    public static String[] getActivityRecognitionPermissions() {
        return new String[]{Manifest.permission.ACTIVITY_RECOGNITION};
    }

    @NonNull @AnyThread @CheckResult
    public static String[] getBackgroundLocationPermissions() {
        return new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION};
    }

    @NonNull @AnyThread @CheckResult
    public static String[] getForegroundLocationPermissions() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }
}
