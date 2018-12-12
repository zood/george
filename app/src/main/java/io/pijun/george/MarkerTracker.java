package io.pijun.george;

import android.util.LongSparseArray;

import com.google.android.gms.maps.model.Marker;

import java.util.HashMap;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
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
    void clear() {
        mIdToMarker.clear();
        mIdToLocation.clear();
        mMarkerToId.clear();
    }

    @Nullable
    @UiThread
    Marker getById(long friendId) {
        return mIdToMarker.get(friendId);
    }

    @UiThread
    FriendLocation getLocation(Marker marker) {
        long id = mMarkerToId.get(marker);
        return mIdToLocation.get(id);
    }

    @NonNull
    @UiThread
    Set<Marker> getMarkers() {
        return mMarkerToId.keySet();
    }

    @Nullable
    @UiThread
    Marker removeMarker(long friendId) {
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
    void updateLocation(long friendId, FriendLocation loc) {
        mIdToLocation.put(friendId, loc);
    }
}
