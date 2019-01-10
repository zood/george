package xyz.zood.george;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import java.util.Locale;

public class SafetyNumber {

    @NonNull @CheckResult @AnyThread
    public static String toSafetyNumber(@NonNull byte[] bytes, int columns) {
        if (bytes.length % 2 != 0) {
            throw new RuntimeException("Bytes count should be a multiple of 2");
        }
        if (columns < 1) {
            throw new RuntimeException("Column count should be >= 1");
        }

        int col = 0;
        StringBuilder sn = new StringBuilder();
        boolean startedRow = true;
        for (int i=0; i<bytes.length; i+=2) {
            int val = toUnsignedInt(bytes[i]) << 8;
            val |= toUnsignedInt(bytes[i+1]);

            if (!startedRow) {
                sn.append("  ");
            }
            sn.append(String.format(Locale.US, "%05d", val));
            col += 1;
            if (col >= columns) {
                sn.append('\n');
                startedRow = true;
                col = 0;
            } else {
                startedRow = false;
            }
        }

        return sn.toString().trim();
    }

    private static int toUnsignedInt(byte b) {
        return ((int) b) & 0xff;
    }

}
