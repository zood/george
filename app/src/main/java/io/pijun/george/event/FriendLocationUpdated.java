package io.pijun.george.event;

import io.pijun.george.models.FriendLocation;

public class FriendLocationUpdated {

    public final FriendLocation location;

    public FriendLocationUpdated(FriendLocation loc) {
        this.location = loc;
    }

}
