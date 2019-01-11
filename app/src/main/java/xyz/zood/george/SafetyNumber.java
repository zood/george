package xyz.zood.george;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import java.util.Locale;

public class SafetyNumber {

    @NonNull @CheckResult @AnyThread
    public static String toSafetyNumber(@NonNull byte[] bytes, int columns, @NonNull String spacing) {
        if (bytes.length % 2 != 0) {
            throw new RuntimeException("Bytes count should be a multiple of 2");
        }
        if (columns < 1) {
            throw new RuntimeException("Column count should be >= 1");
        }

        int col = 0;
        StringBuilder sn = new StringBuilder();
        for (int i=0; i<bytes.length; i+=2) {
            int val = toUnsignedInt(bytes[i]) << 8;
            val |= toUnsignedInt(bytes[i+1]);

            sn.append(String.format(Locale.US, "%05d", val));
            col += 1;
            if (col >= columns) {
                sn.append('\n');
                col = 0;
            } else {
                sn.append(spacing);
            }
        }

        return sn.toString().trim();
    }

    private static int toUnsignedInt(byte b) {
        return ((int) b) & 0xff;
    }

}
