package io.pijun.george.api;

public enum CommType {

    LocationInfo("location_info"),
    LocationSharingRequest("location_sharing_request"),
    LocationSharingGrant("location_sharing_grant"),
    Unknown("unknown");

    public final String val;

    CommType(String val) {
        this.val = val;
    }

    public static CommType get(String val) {
        if (val == null) {
            return Unknown;
        }

        for (CommType ct : values()) {
            if (ct.val.equals(val)) {
                return ct;
            }
        }

        return Unknown;
    }

}
