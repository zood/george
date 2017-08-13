package io.pijun.george.event;

import android.support.annotation.NonNull;

public class FriendRemoved {
    public final long friendId;
    public final long userId;
    @NonNull public final String username;

    public FriendRemoved(long friendId, long userId, @NonNull String username) {
        this.friendId = friendId;
        this.userId = userId;
        this.username = username;
    }
}
