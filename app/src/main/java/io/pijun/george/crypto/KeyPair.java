package io.pijun.george.crypto;

import io.pijun.george.Hex;

public class KeyPair {

    public byte[] publicKey;
    public byte[] secretKey;

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
