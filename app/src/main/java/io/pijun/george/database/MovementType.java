package io.pijun.george.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum MovementType {
    Vehicle("vehicle"),
    Bicycle("bicycle"),
    OnFoot("on_foot"),
    Running("running"),
    Stationary("stationary"),
    Tilting("tilting"),
    Walking("walking"),
    Unknown("unknown");

    public final String val;

    MovementType(@NonNull String val) {
        this.val = val;
    }

    @NonNull
    public static MovementType get(@Nullable String val) {
        if (val == null) {
            return Unknown;
        }

        for (MovementType mt : values()) {
            if (mt.val.equals(val)) {
                return mt;
            }
        }

        return Unknown;
    }
}
