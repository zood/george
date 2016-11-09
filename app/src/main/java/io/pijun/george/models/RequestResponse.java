package io.pijun.george.models;

public enum RequestResponse {

    Granted("granted"),
    Rejected("rejected"),
    NoResponse("no_response");

    public final String val;

    RequestResponse(String val) {
        this.val = val;
    }

    public static RequestResponse get(String val) {
        if (val == null) {
            return NoResponse;
        }

        for (RequestResponse r : values()) {
            if (r.val.equals(val)) {
                return r;
            }
        }

        return NoResponse;
    }

}
