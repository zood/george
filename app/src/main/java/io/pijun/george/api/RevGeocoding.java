package io.pijun.george.api;

import android.support.annotation.NonNull;

public class RevGeocoding {
//    String placeId;
    Address address;

    public static class Address {
        // String houseNumber;
        // String road;
        String neighbourhood;
        String suburb;
        String city;
        String county;
        String state;
//        String postcode;
        String country;
//        String countryCode;
    }

    @NonNull
    public String getArea() {
        if (address.neighbourhood != null) {
            return address.neighbourhood;
        } else if (address.suburb != null) {
            return address.suburb;
        } else if (address.city != null) {
            return address.city;
        } else if (address.county != null) {
            return address.county;
        } else if (address.state != null) {
            return address.state;
        } else if (address.country != null) {
            return address.country;
        }

        return "";
    }
}
