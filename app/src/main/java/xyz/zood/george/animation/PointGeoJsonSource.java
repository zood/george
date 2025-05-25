package xyz.zood.george.animation;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;

import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

public class PointGeoJsonSource {

    @NonNull private final GeoJsonSource src;

    private PointGeoJsonSource(@NonNull GeoJsonSource src) {
        this.src = src;
    }

    public static void animateTo(@NonNull GeoJsonSource src, Point start, Point end) {
        PointGeoJsonSource pgjs = new PointGeoJsonSource(src);
        ValueAnimator ptAnimator = ObjectAnimator.ofObject(pgjs, "position", new PointEvaluator(), start, end);
        ptAnimator.setDuration(500);
        ptAnimator.start();
    }

    public void setPosition(Point p) {
        src.setGeoJson(Feature.fromGeometry(p));
    }
}
