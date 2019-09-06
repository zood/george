package xyz.zood.george;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.google.android.gms.maps.MapView;

public class GoogleMapLifecycleObserver implements LifecycleObserver {

    @NonNull private final MapView map;

    GoogleMapLifecycleObserver(@NonNull MapView map) {
        this.map = map;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        map.onStart();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        map.onResume();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        map.onPause();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        map.onStop();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        map.onDestroy();
    }
}
