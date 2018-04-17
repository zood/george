package io.pijun.george.api;

import android.location.Location;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.util.Arrays;

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
    public Float accuracy;
    public Float speed;
    public Float bearing;
    public String movement;

    // location_update_request_received
    @LocationUpdateRequestAction public String locationUpdateRequestAction;

    // debug
    public String debugData;

    @Retention(SOURCE)
    @StringDef({
            LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON,
            LOCATION_UPDATE_REQUEST_ACTION_STARTING
    })
    @interface LocationUpdateRequestAction {}
    public static final String LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON = "too_soon";
    public static final String LOCATION_UPDATE_REQUEST_ACTION_STARTING = "starting";

    private UserComm() {}

    @NonNull @CheckResult
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

    public static UserComm newLocationUpdateRequestReceived(@LocationUpdateRequestAction String action) {
        UserComm c = new UserComm();
        c.type = CommType.LocationUpdateRequestReceived;
        c.locationUpdateRequestAction = action;
        return c;
    }

    @NonNull @CheckResult
    public static UserComm newLocationInfo(@NonNull Location l, @Nullable MovementType movement) {
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

        return c;
    }

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
            case Debug:
                if (debugData == null) {
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
                //noinspection RedundantIfStatement
                if (time <= 0) {
                    return false;
                }
                return true;
            case LocationUpdateRequest:
                return true;
            case LocationUpdateRequestReceived:
                if (locationUpdateRequestAction == null) {
                    return false;
                }
                if (!locationUpdateRequestAction.equals(LOCATION_UPDATE_REQUEST_ACTION_STARTING) &&
                        !locationUpdateRequestAction.equals(LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON)) {
                    return false;
                }
                return true;
            default:
                L.i("encountered unknown commtype. probably from a different version of Pijun");
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
        return s.getBytes();
    }

    @Override
    public String toString() {
        return "UserComm{" +
                "type=" + type +
                ", dropBox=" + Arrays.toString(dropBox) +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", time=" + time +
                ", accuracy=" + accuracy +
                ", speed=" + speed +
                ", bearing=" + bearing +
                ", locationUpdateRequestAction=" + locationUpdateRequestAction +
                '}';
    }
}
