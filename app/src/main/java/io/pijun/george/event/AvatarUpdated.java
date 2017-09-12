package io.pijun.george.event;

import android.support.annotation.Nullable;

public class AvatarUpdated {

    /**
     * The username of the person who's avatar was updated. A <code>null</code> means the avatar of the
     * logged in user was updated.
     */
    @Nullable
    public final String username;

    public AvatarUpdated(@Nullable String username) {
        this.username = username;
    }
}
