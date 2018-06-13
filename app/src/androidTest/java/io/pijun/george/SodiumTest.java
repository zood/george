package io.pijun.george;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;

import io.pijun.george.crypto.EncryptedData;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SodiumTest {

    private SecureRandom random = new SecureRandom();

    @Before
    public static void setUp() {
        Sodium.init();
    }

    @Test
    public void testSymmetricCrypto() {
        byte[] msg = new byte[256];
        random.nextBytes(msg);
        byte[] key = new byte[Sodium.getSymmetricKeyLength()];
        random.nextBytes(key);

        EncryptedData encryptedData = Sodium.symmetricKeyEncrypt(msg, key);
        assertNotNull(encryptedData);

        byte[] decrypted = Sodium.symmetricKeyDecrypt(encryptedData.cipherText, encryptedData.nonce, key);
        assertArrayEquals(msg, decrypted);
    }

}
