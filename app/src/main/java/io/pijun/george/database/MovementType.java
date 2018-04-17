package io.pijun.george.database;

import com.google.android.gms.location.DetectedActivity;

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
            case DetectedActivity.ON_BICYCLE:
                return Bicycle;
            case DetectedActivity.ON_FOOT:
                return OnFoot;
            case DetectedActivity.RUNNING:
                return Running;
            case DetectedActivity.STILL:
                return Stationary;
            case DetectedActivity.TILTING:
                return Tilting;
            case DetectedActivity.WALKING:
                return Walking;
            default:
                return Unknown;
        }
    }
}
