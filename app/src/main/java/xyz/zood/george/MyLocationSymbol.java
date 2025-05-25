package xyz.zood.george;

import static org.maplibre.android.style.layers.Property.CIRCLE_PITCH_ALIGNMENT_MAP;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.TransitionOptions;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

import io.pijun.george.view.MyLocationView;
import xyz.zood.george.animation.PointGeoJsonSource;
import xyz.zood.george.animation.SymbolPosition;

public class MyLocationSymbol implements MapLibreMap.OnCameraMoveListener {

    @NonNull private final SymbolManager symbolManager;
    @NonNull private final Style style;
    @NonNull private final MapLibreMap map;

    private boolean createdSymbol = false;

    private GeoJsonSource circleSource;
    private Symbol myDot;
    private CircleLayer errCircle;
    private Location lastLocation;
    private double lastZoom;

    public MyLocationSymbol(@NonNull SymbolManager symbolManager, @NonNull MapLibreMap map, @NonNull Style style) {
        this.symbolManager = symbolManager;
        this.style = style;
        this.map = map;
        this.lastZoom = map.getZoom();
    }

    private float accToPixels(@NonNull Location l) {
        // https://groups.google.com/g/google-maps-js-api-v3/c/hDRO4oHVSeM/m/osOYQYXg2oUJ
        double metersPerPixel = 156543.03392 * Math.cos(l.getLatitude() * Math.PI / 180) / Math.pow(2, map.getZoom());
        return (float) (l.getAccuracy() / metersPerPixel);
    }

    @UiThread
    private void createSymbol(@NonNull Context ctx, @NonNull Location location) {
        lastLocation = location;

        // create the blue dot that represents our location
        Bitmap bitmap = MyLocationView.getBitmap(ctx);
        String myBlueDotSymbolName = "my-blue-dot-symbol";
        style.addImage(myBlueDotSymbolName, bitmap);
        SymbolOptions so = new SymbolOptions()
                .withLatLng(new LatLng(location.getLatitude(), location.getLongitude()))
                .withIconImage(myBlueDotSymbolName)
                .withDraggable(false);
        myDot = symbolManager.create(so);

        // create the error circle that represents the location value's accuracy
        Point pt = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        Feature f = Feature.fromGeometry(pt);
        String myErrorCircleSource = "my-error-circle-source";
        circleSource = new GeoJsonSource(myErrorCircleSource, f);
        style.addSource(circleSource);
        errCircle = new CircleLayer("my-error-circle-layer-id", myErrorCircleSource);
        errCircle.setProperties(
                PropertyFactory.circleColor(Color.parseColor("#8032b6f4")),
                PropertyFactory.circlePitchAlignment(CIRCLE_PITCH_ALIGNMENT_MAP),
                PropertyFactory.circleRadius(accToPixels(location))
        );
        errCircle.setCircleRadiusTransition(new TransitionOptions(0, 0, true));
        style.addLayerBelow(errCircle, symbolManager.getLayerId());

        // Now that we have created the symbol, we're interested in camera movements so we can
        // adjust the error circle as needed.
        map.addOnCameraMoveListener(this);
    }

    @Nullable
    @UiThread
    public LatLng getLatLng() {
        if (!createdSymbol) {
            return null;
        }

        return myDot.getLatLng();
    }

    @Nullable
    public Symbol getSymbol() {
        return myDot;
    }

    @UiThread
    public void updateLocation(@NonNull Context ctx, @NonNull Location l) {
        if (!createdSymbol) {
            createSymbol(ctx, l);
            createdSymbol = true;
            return;
        }

        var lastPoint = Point.fromLngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
        lastLocation = l;

        SymbolPosition.animateTo(symbolManager, myDot, new LatLng(l.getLatitude(), l.getLongitude()));
        PointGeoJsonSource.animateTo(circleSource, lastPoint, Point.fromLngLat(l.getLongitude(), l.getLatitude()));
        errCircle.setProperties(PropertyFactory.circleRadius(accToPixels(l)));
    }

    // region: OnCameraMoveListener

    public void onCameraMove() {
        if (map.getZoom() == lastZoom) {
            // if the zoom is unchanged, we don't want to trigger unnecessary UI work
            return;
        }
        lastZoom = map.getZoom();

        errCircle.setProperties(PropertyFactory.circleRadius(accToPixels(lastLocation)));
    }
}
