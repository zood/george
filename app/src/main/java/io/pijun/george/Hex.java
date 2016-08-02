package io.pijun.george;

public class Hex {

    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (bytes == null) {
            return "<null>";
        }

        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

}
