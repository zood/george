package io.pijun.george;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.google.firebase.crash.FirebaseCrash;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import io.pijun.george.api.LocationIQClient;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.RevGeocoding;
import okhttp3.ResponseBody;
import retrofit2.Response;

class AreaCache {

    private static ConcurrentHashMap<LatLng, String> mCachedAreas = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<LatLng, Boolean> mOngoingRequests = new ConcurrentHashMap<>();

    @WorkerThread
    private static void _fetchArea(@NonNull Context ctx, double lat, double lng, @Nullable ReverseGeocodingListener l) {
        String str = null;  // for debugging a crash
        try {
            Response<ResponseBody> response = LocationIQClient.get(ctx).getReverseGeocoding2("" + lat, "" + lng).execute();
            if (!response.isSuccessful()) {
                ResponseBody body = response.errorBody();
                if (body == null) {
                    L.w("Error retrieving reverse geocoding: null body");
                } else {
                    L.w("Error retrieving reverse geocoding: " + body.string());
                }
                if (l != null) {
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            l.onReverseGeocodingCompleted(null);
                        }
                    });
                }
                return;
            }

            str = response.body().string();
//            L.i("geo string: " + str);
//            bytes = response.raw().body().bytes();
            RevGeocoding rg = OscarClient.sGson.fromJson(str, RevGeocoding.class);
//            RevGeocoding rg = response.body();
            if (rg == null) {
                notifyListener(l, null);
                return;
            }
            String area = rg.getArea();
            mCachedAreas.put(new LatLng(lat, lng), area);
            notifyListener(l, area);
        } catch (IOException e) {
            notifyListener(l, null);
        } catch (Throwable t) {
            if (str == null) {
                FirebaseCrash.log("the string was null, so you're on your own");
                FirebaseCrash.report(t);
            } else {
                FirebaseCrash.log("the string was: " + str);
                FirebaseCrash.report(t);
            }
            notifyListener(l, null);
        } finally {
            LatLng latLng = new LatLng(lat, lng);
            mOngoingRequests.remove(latLng);
        }
    }

    @AnyThread
    static void fetchArea(@NonNull Context ctx, final double lat, final double lng, @Nullable final ReverseGeocodingListener l) {
        final LatLng latLng = new LatLng(lat, lng);
        if (mOngoingRequests.get(latLng) != null) {
            return;
        }
        mOngoingRequests.put(latLng, true);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                _fetchArea(ctx, lat, lng, l);
            }
        });
    }

    @AnyThread
    @Nullable
    static String getArea(double lat, double lng) {
        LatLng ll = new LatLng(lat, lng);
        return mCachedAreas.get(ll);
    }

    private static void notifyListener(@Nullable ReverseGeocodingListener l, @Nullable String area) {
        if (l != null) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    l.onReverseGeocodingCompleted(area);
                }
            });
        }
    }

    interface ReverseGeocodingListener {
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
