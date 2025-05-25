package xyz.zood.george;

import static org.maplibre.android.style.layers.Property.CIRCLE_PITCH_ALIGNMENT_MAP;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.TransitionOptions;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

import java.time.Instant;
import java.util.Locale;

import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import xyz.zood.george.animation.PointGeoJsonSource;
import xyz.zood.george.animation.SymbolPosition;

public class FriendSymbol implements MapLibreMap.OnCameraMoveListener {

    @NonNull private final MapLibreMap map;
    private final long friendId;
    @NonNull private FriendLocation location;
    @NonNull private final Style style;
    @NonNull private final SymbolManager symbolManager;
    private Symbol avatar;
    private CircleLayer errCircle;
    private GeoJsonSource circleSrc;
    @NonNull private String avatarImageName;
    private double lastZoom;

    public FriendSymbol(@NonNull Context ctx, @NonNull Bitmap icon, @NonNull MapLibreMap map, @NonNull SymbolManager sm, @NonNull Style style, @NonNull FriendRecord friend, @NonNull FriendLocation location) {
        this.map = map;
        this.style = style;
        friendId = friend.id;
        this.symbolManager = sm;
        this.avatarImageName = String.format(Locale.US, "friend-%d-%d", friend.id, Instant.now().getNano());
        this.location = location;
        this.lastZoom = map.getZoom();
        createSymbol(ctx, icon, location);


        map.addOnCameraMoveListener(this);
    }

    private void createSymbol(@NonNull Context ctx, @NonNull Bitmap icon, @NonNull FriendLocation l) {
        // create the avatar symbol for the friend
        style.addImage(avatarImageName, icon);
        SymbolOptions so = new SymbolOptions()
                .withLatLng(new LatLng(l.latitude, l.longitude))
                .withIconImage(avatarImageName)
                .withDraggable(false);
        avatar = symbolManager.create(so);

        // create the error circle
        Point pt = Point.fromLngLat(l.longitude, l.latitude);
        Feature f = Feature.fromGeometry(pt);
        String srcId = String.format(Locale.US, "friend-%d-err-circle-source", friendId);
        circleSrc = new GeoJsonSource(srcId, f);
        style.addSource(circleSrc);
        String layerId = String.format(Locale.US, "friend-%d-circle-layer", friendId);
        errCircle = new CircleLayer(layerId, srcId);
        errCircle.setProperties(
                PropertyFactory.circleColor(ctx.getColor(R.color.error_circle_fill)),
                PropertyFactory.circlePitchAlignment(CIRCLE_PITCH_ALIGNMENT_MAP),
                PropertyFactory.circleRadius(l.getAccuracyRadiusInPixels(map.getZoom())),
                PropertyFactory.visibility(Property.NONE)
        );
        errCircle.setCircleRadiusTransition(new TransitionOptions(0, 0, true));
        style.addLayerBelow(errCircle, symbolManager.getLayerId());
    }

    public void deleteSymbol() {
        style.removeLayer(errCircle);
        symbolManager.delete(avatar);
        style.removeSource(circleSrc);
        style.removeImage(avatarImageName);
    }

    public long getFriendId() {
        return friendId;
    }

    @NonNull
    @UiThread
    public LatLng getLatLng() {
        return avatar.getLatLng();
    }

    @NonNull
    public FriendLocation getLocation() {
        return location;
    }

    @NonNull
    public Symbol getSymbol() {
        return avatar;
    }

    @UiThread
    public void onAvatarUpdated(@NonNull Bitmap icon) {
        var newImageName = String.format(Locale.US, "friend-%d-%d", friendId, Instant.now().getNano());
        style.addImage(newImageName, icon);
        avatar.setIconImage(newImageName);
        style.removeImage(avatarImageName);
        avatarImageName = newImageName;
        symbolManager.update(avatar);
    }

    public void setErrorCircleVisible(boolean show) {
        var prop = PropertyFactory.visibility(show ? Property.VISIBLE : Property.NONE);
        errCircle.setProperties(prop);
    }

    @UiThread
    public void updateLocation(@NonNull FriendLocation l) {
        var lastPoint = Point.fromLngLat(location.longitude, location.latitude);
        location = l;

        SymbolPosition.animateTo(symbolManager, avatar, new LatLng(l.latitude, l.longitude));
        PointGeoJsonSource.animateTo(circleSrc, lastPoint, Point.fromLngLat(l.longitude, l.latitude));
        var radius = l.getAccuracyRadiusInPixels(map.getZoom());
        errCircle.setProperties(PropertyFactory.circleRadius(radius));
    }

    // region: OnCameraMoveListener

    public void onCameraMove() {
        if (map.getZoom() == lastZoom) {
            // if the zoom is unchanged, we don't want to trigger unnecessary UI work
            return;
        }
        lastZoom = map.getZoom();

        // update the pixel radius of the error circle
        errCircle.setProperties(PropertyFactory.circleRadius(location.getAccuracyRadiusInPixels(lastZoom)));
    }

    @NonNull
    public String toString() {
        return Long.toString(friendId);
    }
}
