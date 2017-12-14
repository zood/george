package io.pijun.george;

import android.animation.TypeEvaluator;
import android.annotation.SuppressLint;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.app.AlertDialog;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.pijun.george.event.UserLoggedOut;
import io.pijun.george.service.FcmTokenRegistrar;

@SuppressWarnings("WeakerAccess")
public final class Utils {

    @AnyThread
    public static void logOut(@NonNull Context ctx, @Nullable UiRunnable completion) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                String fcmToken = Prefs.get(ctx).getFcmToken();
                ctx.startService(FcmTokenRegistrar.newIntent(ctx, true, fcmToken));
                Prefs.get(ctx).clearAll();
                DB.get(ctx).deleteUserData();
                JobScheduler jobScheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (jobScheduler != null) { // it will never be null
                    jobScheduler.cancelAll();
                }

                App.postOnBus(new UserLoggedOut());
                if (completion != null) {
                    App.runOnUiThread(completion);
                }
            }
        });
    }

    public static Map<String, Object> map(Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("You need to provide an even number of arguments. (Keys and values)");
        }
        HashMap<String, Object> map = new HashMap<>();
        for (int i=0; i<args.length; i++) {
            map.put((String)args[i], args[i+1]);
            i += 1;
        }

        return map;
    }

    @SuppressLint("WrongThread")
    @AnyThread
    public static void showAlert(final Context ctx, @StringRes final int titleId, @StringRes final int msgId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            App.runOnUiThread(() -> _showAlert(ctx, titleId, msgId));
            return;
        }

        _showAlert(ctx, titleId, msgId);
    }

    @UiThread
    private static void _showAlert(Context ctx, @StringRes int titleId, @StringRes int msgId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx, R.style.AlertDialogTheme);
        if (titleId != 0) {
            builder.setTitle(titleId);
        }
        if (msgId != 0) {
            builder.setMessage(msgId);
        }
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    @SuppressLint("WrongThread")
    @AnyThread
    public static void showStringAlert(final Context ctx, final CharSequence title, final CharSequence msg) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            App.runOnUiThread(() -> _showStringAlert(ctx, title, msg));
            return;
        }

        _showStringAlert(ctx, title, msg);
    }

    @UiThread
    private static void _showStringAlert(final Context ctx, CharSequence title, CharSequence msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx, R.style.AlertDialogTheme);
        if (title != null) {
            builder.setTitle(title);
        }
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    final static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.
        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            double lat = startValue.latitude
                    + ((endValue.latitude - startValue.latitude) * fraction);
            double lng = startValue.longitude
                    + ((endValue.longitude - startValue.longitude) * fraction);
            return new LatLng(lat, lng);
        }
    }

    final static class DoubleEvaluator implements TypeEvaluator<Double> {
        @Override
        public Double evaluate(float fraction, Double startVal, Double endVal) {
            return startVal + ((endVal - startVal) * fraction);
        }
    }

    interface DrawerSwipesListener {
        void onOpenDrawer(float pixels);
        void onCloseDrawer(float pixels, float delta);
        void onFlingCloseDrawer();
        void onFlingOpenDrawer();
        boolean onSettleDrawer();
    }

    public final static class DrawerActionRecognizer extends GestureDetector.SimpleOnGestureListener {
        private boolean isClosed = true;
        private final float screenWidth;
        private boolean isGesturing = false;
        @NonNull
        private DrawerSwipesListener mListener;

        public DrawerActionRecognizer(float screenWidth, @NonNull DrawerSwipesListener l) {
            this.screenWidth = screenWidth;
            this.mListener = l;
        }

        public boolean isGesturing() {
            return isGesturing;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            // if we're already gesturing, don't track additional fingers
            if (isGesturing) {
                return false;
            }
            if (isClosed) {
                // make sure it starts at the edge of the screen
                if (e.getX() < screenWidth * 0.03) {
                    isGesturing = true;
                    return true;
                }
                return false;
            } else {
                // always respond when the drawer is open
                isGesturing = true;
                return true;
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//            L.i("fling - vX: " + velocityX + ", vY: " + velocityY);
            if (velocityX > 200) {
                mListener.onFlingOpenDrawer();
                isClosed = false;
            } else if (velocityX < -200) {
                mListener.onFlingCloseDrawer();
                isClosed = true;
            } else {
                isClosed = !mListener.onSettleDrawer();
            }
            isGesturing = false;

            return true;
        }

        @Override
        public boolean onScroll(MotionEvent down, MotionEvent curr, float distanceX, float distanceY) {
            if (isClosed) {
                float dx = curr.getX() - down.getX();
                dx = Math.max(0, dx);
                mListener.onOpenDrawer(dx);
            } else {
                float dx = down.getX() - curr.getX();
                dx = Math.max(0, dx);
                mListener.onCloseDrawer(dx, distanceX);
            }

            return true;
        }

        public void onUp() {
            isClosed = !mListener.onSettleDrawer();
            isGesturing = false;
        }

        void setClosed(boolean closed) {
            isClosed = closed;
        }
    }

    public static int pixToDps(Context ctx, int pixs) {
        float scale = ctx.getResources().getDisplayMetrics().density;
        return (int)((pixs - 0.5f)/scale);
    }

    public static int dpsToPix(Context ctx, int dps) {
        float scale = ctx.getResources().getDisplayMetrics().density;
        return (int) (dps * scale + 0.5f);
    }

    private static final Pattern sUsernamePattern = Pattern.compile("^[a-z0-9]{5,}$");
    @AnyThread
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return sUsernamePattern.matcher(username.toLowerCase()).matches();
    }

    @StringRes
    public static int getInvalidUsernameReason(@Nullable String username) {
        if (username == null) {
            return R.string.username_missing;
        }
        String lc = username.toLowerCase(Locale.US);
        // Check if it's valid, before making any other assumptions.
        if (sUsernamePattern.matcher(lc).matches()) {
            return 0;
        }

        if (lc.length() < 5) {
            return R.string.too_short;
        }

        return R.string.invalid_characters_msg;
    }

    public static boolean isValidEmail(String email) {
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }

        String local = parts[0];
        if (local.length() == 0 || local.length() > 64) {
            return false;
        }

        // check if we have the format of a domain
        String domain = parts[1];
        if (domain.length() == 0 || domain.length() > 255) {
            return false;
        }
        String[] domainParts = domain.split("\\.");
        if (domainParts.length < 2) {
            return false;
        }

        if (domainParts[domainParts.length-1].length() < 2) {
            return false;
        }
        return true;
    }
}
