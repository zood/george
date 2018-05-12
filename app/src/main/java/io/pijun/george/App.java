package io.pijun.george;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatDelegate;

import com.squareup.otto.Bus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.pijun.george.api.TaskSender;
import io.pijun.george.service.ActivityTransitionHandler;
import io.pijun.george.service.LocationJobService;
import io.pijun.george.service.PassiveLocationService;

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

        registerOnBus(LocationUploader.get());
        TaskSender.get().start(this);
        // perform this in the background because querying the AuthenticationManager the first time
        // will load the Prefs from disk
        runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                if (AuthenticationManager.isLoggedIn(App.this)) {
                    ActivityTransitionHandler.requestUpdates(App.this);
                    LocationJobService.scheduleLocationJobService(App.this);
                    PassiveLocationService.register(App.this);
                }
            }
        });
    }

    public static App getApp() {
        return sApp;
    }

    @AnyThread
    public static void postOnBus(@NonNull final Object passenger) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sApp.mBus.post(passenger);
        } else {
            runOnUiThread(() -> sApp.mBus.post(passenger));
        }
    }

    @AnyThread
    public static void registerOnBus(@NonNull final Object busStop) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sApp.mBus.register(busStop);
        } else {
            runOnUiThread(() -> sApp.mBus.register(busStop));
        }
    }

    @AnyThread
    public static void unregisterFromBus(@NonNull final Object busStop) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            App.sApp.mBus.unregister(busStop);
        } else {
            runOnUiThread(() -> App.sApp.mBus.unregister(busStop));
        }
    }

    @AnyThread
    public static void runOnUiThread(@NonNull UiRunnable r) {
        sApp.mUiThreadHandler.post(r);
    }

    public static void runOnUiThread(@NonNull UiRunnable r, long delay) {
        sApp.mUiThreadHandler.postDelayed(r, delay);
    }

    @AnyThread
    public static void runInBackground(@NonNull WorkerRunnable r) {
        sApp.mExecutor.execute(r);
    }
}
