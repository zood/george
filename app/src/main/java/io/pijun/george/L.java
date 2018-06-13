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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class L {
    private static final String TAG = "Pijun";
    private static volatile FileOutputStream sLogStream;
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    static final String LOG_FILENAME = "logs.txt";
    private static ReadWriteLock sLogLock = new ReentrantReadWriteLock(false);

    public static void i(@NonNull String msg) {
        Log.i(TAG, msg);
        write(msg.getBytes(Constants.utf8));
    }

    public static void d(@NonNull String msg) {
        Log.d(TAG, msg);
        write(msg.getBytes(Constants.utf8));
    }

    public static void w(@NonNull String msg) {
        Log.w(TAG, msg);
        write(msg.getBytes(Constants.utf8));
    }

    public static void w(@NonNull String msg, @NonNull Throwable t) {
        Log.w(TAG, msg, t);
        write(msg.getBytes(Constants.utf8), t);
    }

    public static void e(@NonNull String msg, @NonNull Throwable t) {
        Log.e(TAG, msg, t);
        write(msg.getBytes(Constants.utf8), t);
    }

    private static void write(@NonNull byte[] msg) {
        FileOutputStream stream = getStream();
        sLogLock.writeLock().lock();
        try {
            String time = sDateFormat.format(new Date());
            stream.write(time.getBytes(Constants.utf8));
            stream.write(": ".getBytes(Constants.utf8));
            stream.write(msg);
            stream.write('\n');
        } catch (IOException e) {
            Log.e(TAG, "unable to write a action to the log file", e);
        } finally {
            sLogLock.writeLock().unlock();
        }
    }

    private static void write(@NonNull byte[] msg, @NonNull Throwable t) {
        FileOutputStream stream = getStream();
        sLogLock.writeLock().lock();
        try {
            String time = sDateFormat.format(new Date());
            stream.write(time.getBytes(Constants.utf8));
            stream.write(": ".getBytes(Constants.utf8));
            stream.write(msg);
            stream.write('\n');
            PrintStream ps = new PrintStream(stream);
            t.printStackTrace(ps);
        } catch (IOException e) {
            Log.e(TAG, "unable to write an action to the log file", e);
        } finally {
            sLogLock.writeLock().unlock();
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

    static void resetLog(@NonNull Context context) {
        sLogLock.writeLock().lock();
        try {
            getStream().close();
            sLogStream = context.openFileOutput(LOG_FILENAME, Context.MODE_PRIVATE);
        } catch (IOException ioe) {
            Log.e(TAG, "error closing or resetting log file", ioe);
        } finally {
            sLogLock.writeLock().unlock();
        }
    }
}
