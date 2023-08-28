package xyz.zood.george;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import io.pijun.george.App;

public class AppInForegroundObserver implements DefaultLifecycleObserver {

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        App.isInForeground = true;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        App.isInForeground = false;
    }

}
