package io.pijun.george;

public class Hex {

    final private static char[] sHexChars = "0123456789abcdef".toCharArray();
    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return "<null>";
        }
        char[] hex = new char[bytes.length * 2];
        for (int i=0; i<bytes.length; i++) {
            int val = bytes[i] & 0xFF;
            hex[i*2] = sHexChars[val >>> 4];
            hex[i*2+1] = sHexChars[val & 0x0F];
        }
        return new String(hex);
    }

    public static byte[] toBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            return null;
        }
        byte[] data = new byte[len / 2];
        for (int i=0; i<len; i+=2) {
            data[i/2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) +
                        Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

}
