package xyz.zood.george;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.maps.MapView;

public class GoogleMapLifecycleObserver implements DefaultLifecycleObserver {

    @NonNull private final MapView map;

    GoogleMapLifecycleObserver(@NonNull MapView map) {
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
