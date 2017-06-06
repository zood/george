package io.pijun.george;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import io.pijun.george.api.LocationIQClient;
import io.pijun.george.api.RevGeocoding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AreaCache {

    private static ConcurrentHashMap<LatLng, String> mCachedAreas = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<LatLng, Boolean> mOngoingRequests = new ConcurrentHashMap<>();

    @AnyThread
    public static void fetchArea(@NonNull Context ctx, final double lat, final double lng, @Nullable final ReverseGeocodingListener l) {
        L.i("AreaCache.fetchArea");
        final LatLng latLng = new LatLng(lat, lng);
        if (mOngoingRequests.get(latLng) != null) {
            return;
        }
        mOngoingRequests.put(latLng, true);
        LocationIQClient.get(ctx).getReverseGeocoding("" + lat, "" + lng).enqueue(new Callback<RevGeocoding>() {
            @Override
            public void onResponse(Call<RevGeocoding> call, Response<RevGeocoding> response) {
                L.i("locationiq response");
                try {
                    if (!response.isSuccessful()) {
                        try {
                            L.w("Error retrieving reverse geocoding: " + response.errorBody().string());
                        } catch (IOException ignore) {
                            L.w("Net err when retrieving reverse geocoding");
                        }
                        return;
                    }

                    RevGeocoding rg = response.body();
                    String area = rg.getArea();
                    L.i("iq area: " + area);
                    mCachedAreas.put(new LatLng(lat, lng), area);
                    if (l != null) {
                        l.onReverseGeocodingCompleted(area);
                    }
                } finally {
                    mOngoingRequests.remove(latLng);
                }
            }

            @Override
            public void onFailure(Call<RevGeocoding> call, Throwable t) {
                L.w("Failure retrieving reverse geocoding");
                mOngoingRequests.remove(latLng);
            }
        });
    }

    @AnyThread
    public static String getArea(double lat, double lng) {
        LatLng ll = new LatLng(lat, lng);
        return mCachedAreas.get(ll);
    }

    interface ReverseGeocodingListener {
        void onReverseGeocodingCompleted(@NonNull String area);
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
