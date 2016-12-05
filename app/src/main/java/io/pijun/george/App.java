package io.pijun.george;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.v7.app.AppCompatDelegate;

import com.squareup.otto.Bus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class App extends Application {

    private static volatile App sApp;
    public static boolean isInForeground = false;
    private Handler mUiThreadHandler;
    private ExecutorService mExecutor;
    private Bus mBus;

    @Override
    public void onCreate() {
        sApp = this;
        L.i("========================");
        L.i("      App.onCreate");
        L.i("========================");
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Sodium.init();

        mUiThreadHandler = new Handler();
        mExecutor = Executors.newCachedThreadPool();
        mBus = new Bus();
    }

    public static App getApp() {
        return sApp;
    }

    @AnyThread
    public static void postOnBus(final Object passenger) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sApp.mBus.post(passenger);
        } else {
            runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    sApp.mBus.post(passenger);
                }
            });
        }
    }

    @AnyThread
    public static void registerOnBus(final Object busStop) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sApp.mBus.register(busStop);
        } else {
            runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    sApp.mBus.register(busStop);
                }
            });
        }
    }

    @AnyThread
    public static void unregisterFromBus(final Object busStop) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            App.sApp.mBus.unregister(busStop);
        } else {
            runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    App.sApp.mBus.unregister(busStop);
                }
            });
        }
    }

    @AnyThread
    public static void runOnUiThread(UiRunnable r) {
        sApp.mUiThreadHandler.post(r);
    }

    @AnyThread
    public static void runInBackground(WorkerRunnable r) {
        sApp.mExecutor.execute(r);
        FutureTask<?> future = (FutureTask<?>) sApp.mExecutor.submit(new WorkerRunnable() {
            @Override
            public void run() {

            }
        });
        future.cancel(true);
    }
}
