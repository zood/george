package io.pijun.george.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.List;

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

    MovementType(String val) {
        this.val = val;
    }

    public static MovementType get(String val) {
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

    public static MovementType getByDetectedActivity(int activityId) {
        switch (activityId) {
            case DetectedActivity.IN_VEHICLE:
                return Vehicle;
            default:
                return Unknown;
        }
    }

    @NonNull
    public static String serialize(@Nullable List<MovementType> movements) {
        if (movements == null || movements.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (MovementType mt : movements) {
            sb.append(sep);
            sb.append(mt.val);
            sep = "|";
        }

        return sb.toString();
    }

    @NonNull
    public static List<MovementType> deserialize(@Nullable String m) {
        ArrayList<MovementType> movements = new ArrayList<>();
        if (m == null || m.length() == 0) {
            return movements;
        }

        String[] parts = m.split("|");
        for (String p : parts) {
            MovementType mt = get(p);
            if (mt == Unknown) {
                continue;
            }
            movements.add(mt);
        }

        return movements;
    }
}
