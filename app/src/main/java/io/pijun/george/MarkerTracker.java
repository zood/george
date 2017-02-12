package io.pijun.george;

import android.support.v4.util.LongSparseArray;

import com.mapbox.mapboxsdk.annotations.Marker;

import java.util.HashMap;
import java.util.Set;

import io.pijun.george.models.FriendLocation;

public class MarkerTracker {

    private LongSparseArray<Marker> mIdToMarker = new LongSparseArray<>();
    private LongSparseArray<FriendLocation> mIdToLocation = new LongSparseArray<>();
    private HashMap<Marker, Long> mMarkerToId = new HashMap<>();

    public void add(Marker marker, long friendId, FriendLocation loc) {
        mIdToMarker.put(friendId, marker);
        mIdToLocation.put(friendId, loc);
        mMarkerToId.put(marker, friendId);
    }

    public void clear() {
        mIdToMarker.clear();
        mIdToLocation.clear();
        mMarkerToId.clear();
    }

    public Marker getById(long friendId) {
        return mIdToMarker.get(friendId);
    }

    public Set<Marker> getMarkers() {
        return mMarkerToId.keySet();
    }

    public void updateLocation(long friendId, FriendLocation loc) {
        mIdToLocation.put(friendId, loc);
    }

    public FriendLocation getLocation(Marker marker) {
        long id = mMarkerToId.get(marker);
        return mIdToLocation.get(id);
    }
}
