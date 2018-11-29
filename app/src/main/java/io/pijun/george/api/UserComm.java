package io.pijun.george.api;

import android.location.Location;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.CheckResult;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.database.MovementType;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class UserComm {

    public CommType type;
    public byte[] dropBox;

    public byte[] avatar;

    // location_info
    public double latitude;
    public double longitude;
    public long time;
    @Nullable
    public Float accuracy;
    @Nullable public Float speed;
    @Nullable public Float bearing;
    @Nullable public String movement;
    @Nullable public Integer batteryLevel;

    // location_update_request_received
    @LocationUpdateRequestAction public String locationUpdateRequestAction;

    // debug
    public String debugData;

    // for device_info comm type
    @SuppressWarnings("WeakerAccess")
    public DeviceInfo deviceInfo;

    @Retention(SOURCE)
    @StringDef({
            LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON,
            LOCATION_UPDATE_REQUEST_ACTION_STARTING,
            LOCATION_UPDATE_REQUEST_ACTION_FINISHED
    })
    public @interface LocationUpdateRequestAction {}
    public static final String LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON = "too_soon";
    public static final String LOCATION_UPDATE_REQUEST_ACTION_STARTING = "starting";
    public static final String LOCATION_UPDATE_REQUEST_ACTION_FINISHED = "finished";

    private UserComm() {}

    @NonNull
    @CheckResult
    public static UserComm newAvatarRequest() {
        UserComm c = new UserComm();
        c.type = CommType.AvatarRequest;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newAvatarUpdate(@NonNull byte[] avatarData) {
        UserComm c = new UserComm();
        c.type = CommType.AvatarUpdate;
        c.avatar = avatarData;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newDebug(@NonNull String data) {
        UserComm c = new UserComm();
        c.type = CommType.Debug;
        c.debugData = data;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newDeviceInfo(@NonNull DeviceInfo deviceInfo) {
        UserComm c = new UserComm();
        c.type = CommType.DeviceInfo;
        c.deviceInfo = deviceInfo;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newLocationSharingGrant(@NonNull byte[] boxId) {
        UserComm c = new UserComm();
        c.type = CommType.LocationSharingGrant;
        c.dropBox = boxId;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newLocationSharingRevocation() {
        UserComm c = new UserComm();
        c.type = CommType.LocationSharingRevocation;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newLocationUpdateRequest() {
        UserComm c = new UserComm();
        c.type = CommType.LocationUpdateRequest;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newLocationUpdateRequestReceived(@LocationUpdateRequestAction String action) {
        UserComm c = new UserComm();
        c.type = CommType.LocationUpdateRequestReceived;
        c.locationUpdateRequestAction = action;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newLocationInfo(@NonNull Location l, @Nullable MovementType movement, @IntRange(from=0, to=100) Integer batteryLevel) {
        UserComm c = new UserComm();
        c.type = CommType.LocationInfo;
        c.latitude = l.getLatitude();
        c.longitude = l.getLongitude();
        c.time = l.getTime();
        if (l.hasAccuracy()) {
            c.accuracy = l.getAccuracy();
        }
        if (l.hasSpeed()) {
            c.speed = l.getSpeed();
        }
        if (l.hasBearing() && l.getBearing() != 0.0f) {
            c.bearing = l.getBearing();
        }
        c.movement = movement == null ? null : movement.val;
        c.batteryLevel = batteryLevel;

        return c;
    }

    public static UserComm newScream() {
        UserComm c = new UserComm();
        c.type = CommType.Scream;

        return c;
    }

    public static UserComm newScreamBegan() {
        UserComm c = new UserComm();
        c.type = CommType.ScreamBegan;

        return c;
    }

    @SuppressWarnings({"RedundantIfStatement", "BooleanMethodIsAlwaysInverted"})
    public boolean isValid() {
        if (type == null) {
            return false;
        }
        switch (type) {
            case AvatarRequest:
                return true;
            case AvatarUpdate:
                if (avatar == null) {
                    return false;
                }
                return true;
            case BrowseDevices:
                return true;
            case Debug:
                if (debugData == null) {
                    return false;
                }
                return true;
            case DeviceInfo:
                if (deviceInfo == null) {
                    return false;
                }
                return true;
            case LocationSharingGrant:
                //noinspection RedundantIfStatement
                if (dropBox == null || dropBox.length != Constants.DROP_BOX_ID_LENGTH) {
                    return false;
                }
                return true;
            case LocationSharingRevocation:
                return true;
            case LocationInfo:
                if (latitude < -90 || latitude > 90) {
                    return false;
                }
                if (longitude < -180 || longitude > 180) {
                    return false;
                }
                if (time <= 0) {
                    return false;
                }
                if (batteryLevel != null) {
                    if (batteryLevel < -1 || batteryLevel > 100) {
                        return false;
                    }
                }
                return true;
            case LocationUpdateRequest:
                return true;
            case LocationUpdateRequestReceived:
                if (locationUpdateRequestAction == null) {
                    return false;
                }
                if (!locationUpdateRequestAction.equals(LOCATION_UPDATE_REQUEST_ACTION_STARTING) &&
                        !locationUpdateRequestAction.equals(LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON) &&
                        !locationUpdateRequestAction.equals(LOCATION_UPDATE_REQUEST_ACTION_FINISHED)) {
                    return false;
                }
                return true;
            case Scream:
                return true;
            case ScreamBegan:
                return true;
            default:
                L.i("encountered unknown commtype. probably from a different version of Zood");
                return false;
        }
    }

    @CheckResult
    public static UserComm fromJSON(byte[] bytes) {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(bytes));
        return OscarClient.sGson.fromJson(isr, UserComm.class);
    }

    @CheckResult
    @NonNull
    public byte[] toJSON() {
        String s = OscarClient.sGson.toJson(this);
        return s.getBytes(Constants.utf8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserComm userComm = (UserComm) o;
        return Double.compare(userComm.latitude, latitude) == 0 &&
                Double.compare(userComm.longitude, longitude) == 0 &&
                time == userComm.time &&
                type == userComm.type &&
                Arrays.equals(dropBox, userComm.dropBox) &&
                Arrays.equals(avatar, userComm.avatar) &&
                Objects.equals(accuracy, userComm.accuracy) &&
                Objects.equals(speed, userComm.speed) &&
                Objects.equals(bearing, userComm.bearing) &&
                Objects.equals(movement, userComm.movement) &&
                Objects.equals(batteryLevel, userComm.batteryLevel) &&
                Objects.equals(locationUpdateRequestAction, userComm.locationUpdateRequestAction) &&
                Objects.equals(debugData, userComm.debugData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, latitude, longitude, time, accuracy, speed, bearing, movement, batteryLevel, locationUpdateRequestAction, debugData, deviceInfo);
        result = 31 * result + Arrays.hashCode(dropBox);
        result = 31 * result + Arrays.hashCode(avatar);
        return result;
    }
}
