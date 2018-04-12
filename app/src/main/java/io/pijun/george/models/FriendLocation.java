package io.pijun.george.models;

import java.util.List;

import io.pijun.george.api.CommType;
import io.pijun.george.api.UserComm;

public class FriendLocation {

    public final long friendId;
    public final double latitude;
    public final double longitude;
    public final long time;
    public final Float accuracy;
    public final Float speed;
    // The person's bearing, in degrees
    public final Float bearing;
    public final List<MovementType> movements;

    public FriendLocation(long friendId, UserComm comm) {
        this(friendId, comm.latitude, comm.longitude, comm.time, comm.accuracy, comm.speed, comm.bearing, MovementType.deserialize(comm.movements));

        if (comm.type != CommType.LocationInfo) {
            throw new IllegalArgumentException("Comm must be a LocationInfo message");
        }
    }

    public FriendLocation(long id, double lat, double lng, long time, Float accuracy, Float speed, Float bearing, List<MovementType> movements) {
        this.friendId = id;
        this.latitude = lat;
        this.longitude = lng;
        this.time = time;
        this.accuracy = accuracy;
        this.speed = speed;
        this.bearing = bearing;
        this.movements = movements;
    }

    @Override
    public String toString() {
        return "FriendLocation{" +
                "friendId=" + friendId +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", time=" + time +
                ", accuracy=" + accuracy +
                ", speed=" + speed +
                ", bearing=" + bearing +
                ", movements='" + movements + '\'' +
                '}';
    }

}
