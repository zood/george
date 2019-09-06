package io.pijun.george;

import android.util.LongSparseArray;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.google.android.gms.maps.model.Marker;

import java.util.HashMap;

import io.pijun.george.database.FriendLocation;

public class MarkerTracker {

    private final LongSparseArray<Marker> mIdToMarker = new LongSparseArray<>();
    private final LongSparseArray<FriendLocation> mIdToLocation = new LongSparseArray<>();
    private final HashMap<Marker, Long> mMarkerToId = new HashMap<>();

    @UiThread
    public void add(Marker marker, long friendId, FriendLocation loc) {
        mIdToMarker.put(friendId, marker);
        mIdToLocation.put(friendId, loc);
        mMarkerToId.put(marker, friendId);
    }

    @UiThread
    public void clear() {
        mIdToMarker.clear();
        mIdToLocation.clear();
        mMarkerToId.clear();
    }

    @Nullable
    @UiThread
    public Marker getById(long friendId) {
        return mIdToMarker.get(friendId);
    }

    @Nullable
    @UiThread
    public FriendLocation getLocation(Marker marker) {
        Long id = mMarkerToId.get(marker);
        if (id == null) {
            return null;
        }
        return mIdToLocation.get(id);
    }

    @Nullable
    @UiThread
    public Marker removeMarker(long friendId) {
        Marker marker = mIdToMarker.get(friendId);
        if (marker == null) {
            return null;
        }
        mIdToMarker.remove(friendId);
        mIdToLocation.remove(friendId);
        mMarkerToId.remove(marker);
        return marker;
    }

    @UiThread
    public void updateLocation(long friendId, FriendLocation loc) {
        mIdToLocation.put(friendId, loc);
    }
}
