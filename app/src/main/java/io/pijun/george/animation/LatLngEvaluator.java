package io.pijun.george.animation;

import android.animation.TypeEvaluator;

import com.google.android.gms.maps.model.LatLng;

public final class LatLngEvaluator implements TypeEvaluator<LatLng> {
    // Method is used to interpolate the marker animation.
    @Override
    public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
        double lat = startValue.latitude
                + ((endValue.latitude - startValue.latitude) * fraction);
        double lng = startValue.longitude
                + ((endValue.longitude - startValue.longitude) * fraction);
        return new LatLng(lat, lng);
    }
}
