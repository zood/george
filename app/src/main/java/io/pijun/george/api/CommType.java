package io.pijun.george.api;

public enum CommType {

    LocationInfo("location_info"),
    LocationSharingGrant("location_sharing_grant"),
    LocationUpdateRequest("location_update_request"),
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
