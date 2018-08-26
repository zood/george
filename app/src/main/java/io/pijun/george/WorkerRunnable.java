package io.pijun.george;

import androidx.annotation.WorkerThread;

public interface WorkerRunnable extends Runnable {

    @WorkerThread
    void run();

}
