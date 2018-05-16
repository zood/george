package io.pijun.george.api;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.crashlytics.android.Crashlytics;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class AreaCache {

    private static ConcurrentHashMap<LatLng, String> mCachedAreas = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<LatLng, CopyOnWriteArrayList<ReverseGeocodingListener>> mOngoingRequests = new ConcurrentHashMap<>();

    @WorkerThread
    private static void _fetchArea(@NonNull Context ctx, @NonNull LatLng ll) {
        String str = null;  // for debugging a crash
        try {
            Response<ResponseBody> response = LocationIQClient.get(ctx).getReverseGeocoding2("" + ll.lat, "" + ll.lng).execute();
            if (!response.isSuccessful()) {
                ResponseBody body = response.errorBody();
                if (body == null) {
                    L.w("Error retrieving reverse geocoding: null body");
                } else {
                    L.w("Error retrieving reverse geocoding: " + body.string());
                }
                notifyListeners(ll, null);
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                notifyListeners(ll, null);
                return;
            }
            str = body.string();
            RevGeocoding rg = OscarClient.sGson.fromJson(str, RevGeocoding.class);
            if (rg == null) {
                notifyListeners(ll, null);
                return;
            }
            String area = rg.getArea();
            mCachedAreas.put(ll, area);
            notifyListeners(ll, area);
        } catch (IOException e) {
            notifyListeners(ll, null);
        } catch (Throwable t) {
            if (str == null) {
                L.w("the string was null, so you're on your own");
                Crashlytics.logException(t);
            } else {
                L.w("the string was: " + str);
                Crashlytics.logException(t);
            }
            notifyListeners(ll, null);
        } finally {
            mOngoingRequests.remove(ll);
        }
    }

    @AnyThread
    public static void fetchArea(@NonNull Context ctx, final double lat, final double lng, @Nullable final ReverseGeocodingListener l) {
        final LatLng latLng = new LatLng(lat, lng);
        CopyOnWriteArrayList<ReverseGeocodingListener> listeners = mOngoingRequests.get(latLng);
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
        mOngoingRequests.put(latLng, listeners);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                _fetchArea(ctx, latLng);
            }
        });
    }

    @AnyThread
    @Nullable
    public static String getArea(double lat, double lng) {
        LatLng ll = new LatLng(lat, lng);
        return mCachedAreas.get(ll);
    }

    private static void notifyListeners(@NonNull LatLng ll, @Nullable String area) {
        CopyOnWriteArrayList<ReverseGeocodingListener> listeners = mOngoingRequests.get(ll);
        for (ReverseGeocodingListener l : listeners) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    l.onReverseGeocodingCompleted(area);
                }
            });
        }
    }

    public interface ReverseGeocodingListener {
        @UiThread
        void onReverseGeocodingCompleted(@Nullable String area);
    }

    private static class LatLng {
        private final double lat;
        private final double lng;

        LatLng(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LatLng latLng = (LatLng) o;

            if (Double.compare(latLng.lat, lat) != 0) return false;
            if (Double.compare(latLng.lng, lng) != 0) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(lat);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(lng);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }

}
