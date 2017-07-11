package io.pijun.george.api;

import android.location.Location;
import android.support.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import io.pijun.george.Constants;
import io.pijun.george.models.MovementType;

public class UserComm {

    public CommType type;
    public byte[] dropBox;

    // location_info
    public double latitude;
    public double longitude;
    public long time;
    public Float accuracy;
    public Float speed;
    public Float bearing;
    public String movements;

    @NonNull
    public static UserComm newLocationSharingGrant(@NonNull byte[] boxId) {
        UserComm c = new UserComm();
        c.type = CommType.LocationSharingGrant;
        c.dropBox = boxId;
        return c;
    }

    @NonNull
    public static UserComm newLocationSharingRevocation() {
        UserComm c = new UserComm();
        c.type = CommType.LocationSharingRevocation;
        return c;
    }

    public static UserComm newLocationUpdateRequest() {
        UserComm c = new UserComm();
        c.type = CommType.LocationUpdateRequest;
        return c;
    }

    @NonNull
    public static UserComm newLocationInfo(@NonNull Location l, List<MovementType> movements) {
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
        c.movements = MovementType.serialize(movements);

        return c;
    }

    public boolean isValid() {
        switch (type) {
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
            default:
                throw new UnsupportedOperationException("unknown commtype: '" + type.val + "'");
        }
    }

    public static UserComm fromJSON(byte[] bytes) {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(bytes));
        return OscarClient.sGson.fromJson(isr, UserComm.class);
    }

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
                '}';
    }
}
