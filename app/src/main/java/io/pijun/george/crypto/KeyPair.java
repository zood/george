package io.pijun.george.crypto;

import java.util.Arrays;
import java.util.Objects;

import io.pijun.george.Hex;

public class KeyPair {

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PubKey: ");
        sb.append(Hex.toHexString(publicKey));
        sb.append('\n');
        sb.append("SecKey: ");
        sb.append(Hex.toHexString(secretKey));

        return sb.toString();
    }
}
