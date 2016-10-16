package io.pijun.george.api;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class UserComm {

    public CommType type;
    public String note;
    public byte[] dropBox;
    public boolean processed;

    public static UserComm newLocationSharingRequest(String note) {
        UserComm c = new UserComm();
        c.type = CommType.LocationSharingRequest;
        c.note = note;
        return c;
    }

    public static UserComm newLocationSharingGrant(byte[] boxId) {
        UserComm c = new UserComm();
        c.type = CommType.LocationSharingGrant;
        c.dropBox = boxId;
        return c;
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
                ", note='" + note + '\'' +
                ", dropBox=" + Arrays.toString(dropBox) +
                ", processed=" + processed +
                '}';
    }
}
