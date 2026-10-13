package xyz.zood.george.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;

public class ReverseGeocodingCache {

    private static final ConcurrentHashMap<LatLng, RevGeocoding> cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<LatLng, CopyOnWriteArrayList<OnCachedListener>> ongoingRequests = new ConcurrentHashMap<>();

    private ReverseGeocodingCache() {}

    // The blocking getFromLocation() is deprecated as of API 33 in favor of a callback variant, but
    // we're already on a worker thread and it works on every API level we support.
    @SuppressWarnings("deprecation")
    @WorkerThread
    private static void _fetch(@NonNull final Context ctx, @NonNull LatLng ll) {
        try {
            // Devices without a geocoder backend (usually ones without Google Play Services)
            if (!Geocoder.isPresent()) {
                notifyListeners(ll, null);
                return;
            }

            List<Address> addresses = new Geocoder(ctx).getFromLocation(ll.lat, ll.lng, 1);
            if (addresses == null || addresses.isEmpty()) {
                notifyListeners(ll, null);
                return;
            }

            RevGeocoding rg = new RevGeocoding(addresses.get(0));
            if (rg.getAddress().isEmpty()) {
                notifyListeners(ll, null);
                return;
            }
            cache.put(ll, rg);
            notifyListeners(ll, rg);
        } catch (Throwable t) {
            L.w("Reverse geocoding failed", t);
            notifyListeners(ll, null);
        } finally {
            ongoingRequests.remove(ll);
        }
    }

    public static void fetch(@NonNull final Context ctx, final double lat, final double lng, @Nullable final OnCachedListener l) {
        final LatLng latLng = new LatLng(lat, lng);
        CopyOnWriteArrayList<OnCachedListener> listeners = ongoingRequests.get(latLng);
        // Is there already a listener for this LatLng? If so, just add this new listener and get out of here
        if (listeners != null) {
            if (l != null) {
                listeners.add(l);
            }
            return;
        }

        listeners = new CopyOnWriteArrayList<>();
        if (l != null) {
            listeners.add(l);
        }
        ongoingRequests.put(latLng, listeners);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                _fetch(ctx, latLng);
            }
        });
    }

    public static RevGeocoding get(double lat, double lng) {
        LatLng ll = new LatLng(lat, lng);
        return cache.get(ll);
    }

    private static void notifyListeners(@NonNull LatLng ll, @Nullable RevGeocoding addr) {
        CopyOnWriteArrayList<OnCachedListener> listeners = ongoingRequests.get(ll);
        if (listeners == null) {
            return;
        }
        for (OnCachedListener l : listeners) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    l.onReverseGeocodingCached(addr);
                }
            });
        }
    }

    public interface OnCachedListener {
        @UiThread
        void onReverseGeocodingCached(@Nullable RevGeocoding addr);
    }

}
