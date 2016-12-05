package io.pijun.george;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class L {
    private static final String TAG = "Pijun";
    private static volatile FileOutputStream sLogStream;
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    static final String LOG_FILENAME = "logs.txt";

    public static void i(@NonNull String msg) {
        Log.i(TAG, msg);
        write("I", msg.getBytes());
    }

    public static void d(@NonNull String msg) {
        Log.d(TAG, msg);
        write("D", msg.getBytes());
    }

    public static void w(@NonNull String msg) {
        Log.w(TAG, msg);
        write("W", msg.getBytes());
    }

    public static void w(@NonNull String msg, @NonNull Throwable t) {
        Log.w(TAG, msg, t);
        write("W", msg.getBytes(), t);
    }

    public static void e(@NonNull String msg, @NonNull Throwable t) {
        Log.e(TAG, msg, t);
        write("W", msg.getBytes(), t);
    }

    private static void write(@NonNull String severity, @NonNull byte[] msg) {
        FileOutputStream stream = getStream();
        try {
            String time = sDateFormat.format(new Date());
            stream.write(time.getBytes());
            stream.write(' ');
            stream.write(severity.getBytes());
            stream.write(": ".getBytes());
            stream.write(msg);
            stream.write('\n');
        } catch (IOException e) {
            Log.e(TAG, "unable to write a message to the log file", e);
        }
    }

    private static void write(@NonNull String severity, @NonNull byte[] msg, @NonNull Throwable t) {
        FileOutputStream stream = getStream();
        try {
            String time = sDateFormat.format(new Date());
            stream.write(time.getBytes());
            stream.write(' ');
            stream.write(severity.getBytes());
            stream.write(": ".getBytes());
            stream.write(msg);
            stream.write('\n');
            PrintStream ps = new PrintStream(stream);
            t.printStackTrace(ps);
        } catch (IOException e) {
            Log.e(TAG, "unable to write a message to the log file", e);
        }
    }

    private static FileOutputStream getStream() {
        if (sLogStream == null) {
            synchronized (L.class) {
                Context ctx = App.getApp();
                try {
                    sLogStream = ctx.openFileOutput(LOG_FILENAME, Context.MODE_APPEND);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "unable to open log file", e);
                }
            }
        }

        return sLogStream;
    }
}
