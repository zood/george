package xyz.zood.george;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.pijun.george.Constants;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class SafetyNumberTest {

    @Test
    public void testTwoBytes() {
        String expected = "46628";
        // binary: 1011011000100100

        byte[] bytes = new byte[]{(byte)182, (byte)36};
        String actual = SafetyNumber.toSafetyNumber(bytes, 1);
        assertEquals(expected, actual);
    }

    @Test
    public void testLeadingZero() {
        String expected = "05301";
        // binary: 0001010010110101

        byte[] bytes = new byte[]{(byte)20, (byte)181};
        String actual = SafetyNumber.toSafetyNumber(bytes, 1);
        assertEquals(expected, actual);
    }

    @Test
    public void testFourBytesTwoCols() {
        String expected = "46628 05301";
        byte[] bytes = new byte[]{(byte)182, (byte)36, (byte)20, (byte)181};
        String actual = SafetyNumber.toSafetyNumber(bytes, 2);
        assertEquals(expected, actual);
    }

    @Test
    public void testFourBytesOneCol() {
        String expected = "46628\n05301";
        byte[] bytes = new byte[]{(byte)182, (byte)36, (byte)20, (byte)181};
        String actual = SafetyNumber.toSafetyNumber(bytes, 1);
        assertEquals(expected, actual);
    }

    @Test
    public void test32BytesFourCols() {
        String expected = "28789 29728 28526 08308\n26725 08308 25964 27753\n25900 08276 20256 21576\n17696 16962 17185 08481";
        byte[] bytes = "put on the tellie, TO THE BBC!!!".getBytes(Constants.utf8);
        String actual = SafetyNumber.toSafetyNumber(bytes, 4);
        assertEquals(expected, actual);
    }

}
