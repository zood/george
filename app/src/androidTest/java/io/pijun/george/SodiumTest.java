package io.pijun.george;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.Arrays;

import androidx.test.runner.AndroidJUnit4;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.sodium.HashConfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SodiumTest {

    private SecureRandom random = new SecureRandom();

    @BeforeClass
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

    @Test
    public void testPublicKeyCrypto() {
        KeyPair alice = new KeyPair();
        Sodium.generateKeyPair(alice);
        KeyPair bob = new KeyPair();
        Sodium.generateKeyPair(bob);

        byte[] msg1 = "Hello, Bob".getBytes(Constants.utf8);
        byte[] msg2 = "Hello, Alice".getBytes(Constants.utf8);

        // alice sends her message to bob
        EncryptedData msg1Encrypted = Sodium.publicKeyEncrypt(msg1, bob.publicKey, alice.secretKey);
        assertNotNull(msg1Encrypted);

        // bob decrypts alice's message
        byte[] msg1Decrypted = Sodium.publicKeyDecrypt(msg1Encrypted.cipherText, msg1Encrypted.nonce, alice.publicKey, bob.secretKey);
        assertArrayEquals(msg1, msg1Decrypted);

        // bob sends a message to alice
        EncryptedData msg2Encrypted = Sodium.publicKeyEncrypt(msg2, alice.publicKey, bob.secretKey);
        assertNotNull(msg2Encrypted);

        // alice decrypt's alice's message
        byte[] msg2Decrypted = Sodium.publicKeyDecrypt(msg2Encrypted.cipherText, msg2Encrypted.nonce, bob.publicKey, alice.secretKey);
        assertNotNull(msg2Decrypted);
    }

    @Test
    public void testArgon2i13() {
        byte[] password = "something-random".getBytes(Constants.utf8);
        byte[] salt = new byte[HashConfig.Algorithm.Argon2i13.saltLength];
        random.nextBytes(salt);

        HashConfig[] configs = new HashConfig[] {
                new HashConfig(HashConfig.Algorithm.Argon2i13, HashConfig.OpsSecurity.Interactive, HashConfig.MemSecurity.Interactive),
                new HashConfig(HashConfig.Algorithm.Argon2i13, HashConfig.OpsSecurity.Moderate, HashConfig.MemSecurity.Moderate)
//                new HashConfig(HashConfig.Algorithm.Argon2i13, HashConfig.OpsSecurity.Sensitive, HashConfig.MemSecurity.Sensitive)
        };
        int size = 48;

        for (HashConfig cfg : configs) {
            // generate the hash
            byte[] hash = Sodium.stretchPassword(size, password, salt, cfg.alg.sodiumId, cfg.getOpsLimit(), cfg.getMemLimit());
            assertNotNull(hash);
            assertEquals(size, hash.length);
        }
    }

    @Test
    public void testArgon2id13() {
        byte[] password = "something-random".getBytes(Constants.utf8);
        byte[] salt = new byte[HashConfig.Algorithm.Argon2id13.saltLength];
        random.nextBytes(salt);

        HashConfig[] configs = new HashConfig[] {
                new HashConfig(HashConfig.Algorithm.Argon2id13, HashConfig.OpsSecurity.Interactive, HashConfig.MemSecurity.Interactive),
                new HashConfig(HashConfig.Algorithm.Argon2id13, HashConfig.OpsSecurity.Moderate, HashConfig.MemSecurity.Moderate),
                // 1GB is too much memory to request for the emulator
                //new HashConfig(HashConfig.Algorithm.Argon2id13, HashConfig.OpsSecurity.Sensitive, HashConfig.MemSecurity.Sensitive)
        };
        int size = 48;

        for (HashConfig cfg : configs) {
            // generate the hash
            byte[] hash = Sodium.stretchPassword(size, password, salt, cfg.alg.sodiumId, cfg.getOpsLimit(), cfg.getMemLimit());
            assertNotNull(hash);
            assertEquals(size, hash.length);
        }
    }

    @Test
    public void testPasswordStretchingSize() {
        byte[] password = "something-random".getBytes(Constants.utf8);
        byte[] salt = new byte[HashConfig.Algorithm.Argon2i13.saltLength];
        random.nextBytes(salt);

        HashConfig cfg = new HashConfig(HashConfig.Algorithm.Argon2i13, HashConfig.OpsSecurity.Interactive, HashConfig.MemSecurity.Interactive);
        int[] hashSizes = new int[]{32, 64, 96, 128};

        for (int s : hashSizes) {
            // generate the hash
            byte[] hash = Sodium.stretchPassword(s, password, salt, cfg.alg.sodiumId, cfg.getOpsLimit(), cfg.getMemLimit());
            assertNotNull(hash);
            assertEquals(s, hash.length);
        }
    }

    @Test
    public void testMessageToSelf() {
        KeyPair kp = new KeyPair();
        assertEquals(0, Sodium.generateKeyPair(kp));
        byte[] msg = "where's my glisten".getBytes(Constants.utf8);
        EncryptedData encrypted = Sodium.publicKeyEncrypt(msg, kp.publicKey, kp.secretKey);
        assertNotNull(encrypted);
        assertNotNull(encrypted.cipherText);
        assertNotNull(encrypted.nonce);
        if (Arrays.equals(msg, encrypted.cipherText)) {
            fail("Cipher text matched the original message");
        }

        byte[] decrypted = Sodium.publicKeyDecrypt(encrypted.cipherText, encrypted.nonce, kp.publicKey, kp.secretKey);
        assertArrayEquals(msg, decrypted);
    }

}
