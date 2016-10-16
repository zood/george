package io.pijun.george;

import android.support.annotation.UiThread;

public interface UiRunnable extends Runnable {

    @UiThread
    void run();

}
