package io.pijun.george;

import android.app.Application;
import android.os.Handler;
import android.support.v7.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        L.i("App.onCreate");
    }

    public static void runOnUiThread(Runnable r) {
        App.getApp().mUiThreadHandler.post(r);
    }

    public static void runInBackground(Runnable r) {
        App.getApp().mExecutor.submit(r);
    }
}
