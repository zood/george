package xyz.zood.george.animation;

import android.animation.TypeEvaluator;

import org.maplibre.android.geometry.LatLng;

public class LatLngEvaluator implements TypeEvaluator<LatLng> {
    @Override
    public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
        double lat = startValue.getLatitude()
                + ((endValue.getLatitude() - startValue.getLatitude()) * fraction);
        double lng = startValue.getLongitude()
                + ((endValue.getLongitude() - startValue.getLongitude()) * fraction);
        return new LatLng(lat, lng);
    }
}
