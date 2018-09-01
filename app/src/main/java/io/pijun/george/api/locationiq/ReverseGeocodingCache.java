package io.pijun.george.api.locationiq;

import android.content.Context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import io.pijun.george.App;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;
import retrofit2.Response;

public class ReverseGeocodingCache {

    private static ConcurrentHashMap<LatLng, RevGeocoding> cache = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<LatLng, CopyOnWriteArrayList<OnCachedListener>> ongoingRequests = new ConcurrentHashMap<>();

    private ReverseGeocodingCache() {}

    @WorkerThread
    private static void _fetch(@NonNull final Context ctx, @NonNull LatLng ll) {
        try {
            Response<RevGeocoding> response = LocationIQClient.get(ctx).getReverseGeocoding("" + ll.lat, "" + ll.lng).execute();
            if (!response.isSuccessful()) {
                notifyListeners(ll, null);
                return;
            }

            RevGeocoding rg = response.body();
            if (rg == null) {
                notifyListeners(ll, null);
                return;
            }
            cache.put(ll, rg);
            notifyListeners(ll, rg);
        } catch (Throwable t) {
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
