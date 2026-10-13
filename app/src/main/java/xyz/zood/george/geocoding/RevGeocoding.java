package xyz.zood.george.geocoding;

import android.location.Address;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RevGeocoding {
    @Nullable private final String houseNumber;
    @Nullable private final String road;
    @Nullable private final String subLocality;
    @Nullable private final String city;
    @Nullable private final String county;
    @Nullable private final String state;
    @Nullable private final String country;

    RevGeocoding(@NonNull Address address) {
        houseNumber = address.getSubThoroughfare();
        road = address.getThoroughfare();
        subLocality = address.getSubLocality();
        city = address.getLocality();
        county = address.getSubAdminArea();
        state = address.getAdminArea();
        country = address.getCountryName();
    }

    @NonNull
    public String getAddress() {
        if (houseNumber != null && road != null && city != null) {
            return String.format("%s %s, %s", houseNumber, road, city);
        } else if (road != null && city != null) {
            return String.format("%s, %s", road, city);
        } else if (city != null && county != null) {
            return String.format("%s, %s", city, county);
        } else if (city != null && state != null) {
            return String.format("%s, %s", city, state);
        }

        return getArea();
    }

    @NonNull
    public String getArea() {
        if (subLocality != null) {
            return subLocality;
        } else if (city != null) {
            return city;
        } else if (county != null) {
            return county;
        } else if (state != null) {
            return state;
        } else if (country != null) {
            return country;
        }

        return "";
    }
}
