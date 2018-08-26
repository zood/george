package io.pijun.george;

import androidx.annotation.UiThread;

public interface UiRunnable extends Runnable {

    @UiThread
    void run();

}
