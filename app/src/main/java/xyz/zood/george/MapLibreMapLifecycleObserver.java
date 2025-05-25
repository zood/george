package xyz.zood.george;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.maplibre.android.maps.MapView;

public class MapLibreMapLifecycleObserver implements DefaultLifecycleObserver {

    @NonNull private final MapView map;

    MapLibreMapLifecycleObserver(@NonNull MapView map) {
        this.map = map;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        map.onStart();
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        map.onResume();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        map.onPause();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        map.onStop();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        map.onDestroy();
    }
}
