package io.pijun.george.crypto;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import io.pijun.george.Hex;

public class KeyPair implements Parcelable {

    public byte[] publicKey;
    public byte[] secretKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyPair keyPair = (KeyPair) o;
        return Arrays.equals(publicKey, keyPair.publicKey) &&
                Arrays.equals(secretKey, keyPair.secretKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, secretKey);
    }

    @NonNull
    @Override
    public String toString() {
        return "PubKey: " +
                Hex.toHexString(publicKey) +
                '\n' +
                "SecKey: " +
                Hex.toHexString(secretKey);
    }

    //region Parcelable

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeByteArray(publicKey);
        out.writeByteArray(secretKey);
    }

    public static final Parcelable.Creator<KeyPair> CREATOR = new Parcelable.Creator<KeyPair>() {
        @Override
        public KeyPair createFromParcel(Parcel in) {
            KeyPair kp = new KeyPair();
            in.readByteArray(kp.publicKey);
            in.readByteArray(kp.secretKey);
            return kp;
        }

        @Override
        public KeyPair[] newArray(int size) {
            return new KeyPair[size];
        }
    };

    //endregion
}
