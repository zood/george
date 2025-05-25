package xyz.zood.george.animation;

import android.animation.TypeEvaluator;

import org.maplibre.geojson.Point;

public class PointEvaluator implements TypeEvaluator<Point> {

    @Override
    public Point evaluate(float fraction, Point startValue, Point endValue) {
        double lat = startValue.latitude()
                + ((endValue.latitude() - startValue.latitude()) * fraction);
        double lng = startValue.longitude()
                + ((endValue.longitude() - startValue.longitude()) * fraction);
        return Point.fromLngLat(lng, lat);
    }
}
