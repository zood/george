package io.pijun.george;

import android.support.annotation.WorkerThread;

public interface WorkerRunnable extends Runnable {

    @WorkerThread
    void run();

}
