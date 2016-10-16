package io.pijun.george;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.app.AlertDialog;

public class Utils {

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
