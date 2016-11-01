package io.pijun.george.api;

import android.support.annotation.Nullable;

public class ReverseGeocoding {

    public static class AddressComponent {
        public String longName;
        public String shortName;
        public String[] types;
    }

    public static class Result {
//        public AddressComponent[] addressComponents;
        public String formattedAddress;
        public String placeId;
        public String[] types;
    }

    public String status;
    public Result[] results;

    @Nullable
    public String getLocalityAddress() {
        if (status == null || !status.equals("OK")) {
            return null;
        }

        for (Result r : results) {
            // check the types for 'locality'
            for (String t : r.types) {
                if (t.equals("locality")) {
                    return r.formattedAddress;
                }
            }
        }

        return null;
    }
}
