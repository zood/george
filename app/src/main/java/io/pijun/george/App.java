package io.pijun.george;

import android.app.Application;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;

public class App extends Application {

    private static volatile App sApp;
    private Handler mUiThreadHandler;
    private ExecutorService mExecutor;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    public static App getApp() {
        return sApp;
    }

    @Override
    public void onCreate() {
        sApp = this;
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Sodium.init();

        mUiThreadHandler = new Handler();
        mExecutor = Executors.newCachedThreadPool();
    }

    public static void runOnUiThread(UiRunnable r) {
        App.getApp().mUiThreadHandler.post(r);
    }

    public static void runInBackground(WorkerRunnable r) {
        App.getApp().mExecutor.execute(r);
    }
}
