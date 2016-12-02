package io.pijun.george;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.app.AlertDialog;

import java.util.HashMap;
import java.util.Map;

public class Utils {

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

    @AnyThread
    public static void showAlert(final Context ctx, @StringRes final int titleId, @StringRes final int msgId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    _showAlert(ctx, titleId, msgId);
                }
            });
            return;
        }

        //noinspection WrongThread
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

    @AnyThread
    public static void showStringAlert(final Context ctx, final CharSequence title, final CharSequence msg) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    _showStringAlert(ctx, title, msg);
                }
            });
            return;
        }

        //noinspection WrongThread
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

}
