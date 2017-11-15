package io.pijun.george.api;

public enum CommType {

    AvatarRequest("avatar_request"),
    AvatarUpdate("avatar_update"),
    Debug("debug"),
    LocationInfo("location_info"),
    LocationSharingGrant("location_sharing_grant"),
    LocationUpdateRequest("location_update_request"),
    LocationUpdateRequestReceived("location_update_request_received"),
    LocationSharingRevocation("location_sharing_revocation"),
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
