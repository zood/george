package io.pijun.george.event;

public class LocationSharingGranted {

    public final long userId;

    public LocationSharingGranted(long userId) {
        this.userId = userId;
    }

}
