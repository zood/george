package io.pijun.george.api;

import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.Size;
import io.pijun.george.Constants;

@SuppressWarnings("WeakerAccess")
public class DeviceInfo {

    @Size(Constants.DEVICE_ID_SIZE) public final byte[] id;
    public final String manufacturer;
    public final String model;
    public final String os;
    public final String osVersion;

    public DeviceInfo(@Size(Constants.DEVICE_ID_SIZE) byte[] id, String manufacturer, String model, String os, String osVersion) {
        this.id = id;
        this.manufacturer = manufacturer;
        this.model = model;
        this.os = os;
        this.osVersion = osVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceInfo that = (DeviceInfo) o;
        return Arrays.equals(id, that.id) &&
                Objects.equals(manufacturer, that.manufacturer) &&
                Objects.equals(model, that.model) &&
                Objects.equals(os, that.os) &&
                Objects.equals(osVersion, that.osVersion);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(manufacturer, model, os, osVersion);
        result = 31 * result + Arrays.hashCode(id);
        return result;
    }
}
