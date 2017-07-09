package io.pijun.george;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.v7.app.AppCompatDelegate;

import com.mapbox.mapboxsdk.MapboxAccountManager;
import com.mapbox.mapboxsdk.telemetry.MapboxEventManager;
import com.squareup.otto.Bus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        String mapboxToken = getString(R.string.mapbox_access_token);
        MapboxAccountManager.start(this, mapboxToken);
        MapboxEventManager.getMapboxEventManager().setTelemetryEnabled(false);
    }

    public static App getApp() {
        return sApp;
    }

    @AnyThread
    public static void postOnBus(final Object passenger) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sApp.mBus.post(passenger);
        } else {
            runOnUiThread(() -> sApp.mBus.post(passenger));
        }
    }

    @AnyThread
    public static void registerOnBus(final Object busStop) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sApp.mBus.register(busStop);
        } else {
            runOnUiThread(() -> sApp.mBus.register(busStop));
        }
    }

    @AnyThread
    public static void unregisterFromBus(final Object busStop) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            App.sApp.mBus.unregister(busStop);
        } else {
            runOnUiThread(() -> App.sApp.mBus.unregister(busStop));
        }
    }

    @AnyThread
    public static void runOnUiThread(UiRunnable r) {
        sApp.mUiThreadHandler.post(r);
    }

    public static void runOnUiThread(UiRunnable r, long delay) {
        sApp.mUiThreadHandler.postDelayed(r, delay);
    }

    @AnyThread
    public static void runInBackground(WorkerRunnable r) {
        sApp.mExecutor.execute(r);
    }
}
