package io.pijun.george.api.gmaps;

import android.support.annotation.Nullable;

import java.util.Arrays;

public class ReverseGeocoding {

    public static class AddressComponent {
        public String longName;
        public String shortName;
        public String[] types;

        @Override
        public String toString() {
            return "AddressComponent{" +
                    "longName='" + longName + '\'' +
                    ", shortName='" + shortName + '\'' +
                    ", types=" + Arrays.toString(types) +
                    '}';
        }
    }

    public static class Result {
        public String formattedAddress;
        public String placeId;
        public String[] types;

        @Override
        public String toString() {
            return "Result{" +
                    "formattedAddress='" + formattedAddress + '\'' +
                    ", placeId='" + placeId + '\'' +
                    ", types=" + Arrays.toString(types) +
                    '}';
        }
    }

    public String status;
    public Result[] results;

    @Nullable
    public String getLocalityAddress() {
        if (status == null || !status.equals("OK")) {
            return null;
        }

        for (Result r : results) {
            // check the types for 'locality', then 'political' as the fallback
            for (String t : r.types) {
                if (t.equals("locality") || t.equals("political")) {
                    return r.formattedAddress;
                }
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "ReverseGeocoding{" +
                "status='" + status + '\'' +
                ", results=" + Arrays.toString(results) +
                '}';
    }
}
