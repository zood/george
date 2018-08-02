package io.pijun.george;

import android.app.Application;
import android.os.Handler;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.pijun.george.api.TaskSender;
import io.pijun.george.database.DB;
import io.pijun.george.receiver.PassiveLocationReceiver;
import io.pijun.george.receiver.UserActivityReceiver;
import io.pijun.george.service.LocationJobService;

public class App extends Application {

    private static volatile App sApp;
    public static boolean isInForeground = false;
    public static boolean isLimitedShareRunning = false;
    private Handler mUiThreadHandler;
    private ExecutorService mExecutor;

    @Override
    public void onCreate() {
        sApp = this;
        L.i("========================");
        L.i("      App.onCreate");
        L.i("========================");
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        int result = Sodium.init();
        if (result != 0 && result != 1) {
            throw new RuntimeException("sodium_init failed. Unsafe to proceed in this state.");
        }

        mUiThreadHandler = new Handler();
        mExecutor = Executors.newCachedThreadPool();

        DB.init(this, false);
        TaskSender.get().start(this);
        // perform this in the background because querying the AuthenticationManager the first time
        // will load the Prefs from disk
        runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                if (AuthenticationManager.isLoggedIn(App.this)) {
                    LocationJobService.scheduleLocationJobService(App.this);
                    PassiveLocationReceiver.register(App.this);
                    UserActivityReceiver.requestUpdates(App.this);
                }
            }
        });
    }

    public static App getApp() {
        return sApp;
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
