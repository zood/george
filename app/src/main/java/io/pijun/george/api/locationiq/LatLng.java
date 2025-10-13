package io.pijun.george.api.locationiq;

import java.util.Objects;

public class LatLng {
    final double lat;
    final double lng;

    LatLng(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LatLng latLng = (LatLng) o;
        return Double.compare(lat, latLng.lat) == 0 && Double.compare(lng, latLng.lng) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lng);
    }
}
