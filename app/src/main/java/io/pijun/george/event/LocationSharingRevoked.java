package io.pijun.george.event;

/**
 * Created by arash on 7/9/17.
 */

public class LocationSharingRevoked {
    public final long userId;
    public LocationSharingRevoked(long userId) {
        this.userId = userId;
    }
}
