package io.pijun.george;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.security.SecureRandom;

import io.pijun.george.crypto.KeyPair;
import io.pijun.george.crypto.SecretKeyEncryptedMessage;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private byte[] mSecretKey;
    private SecretKeyEncryptedMessage mSecretKeyEncryptedMessage;
    private SecretKeyEncryptedMessage mPublicKeyEncryptedMessage;
    private KeyPair mAliceKeys;
    private KeyPair mBobKeys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onInit(View v) {
        L.i("sodium_init: " + Sodium.init());
    }

    public void onCreateKey(View v) {
        L.i("onCreateKey");
        String password = "foo";
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        mSecretKey = Sodium.createKeyFromPassword(32, password.getBytes(), salt);
        L.i("createKeyVal: " + Hex.toHexString(mSecretKey));
        L.i("salt: " + Hex.toHexString(salt));
    }

    public void onCreateKeyPairs(View v) {
        mAliceKeys = new KeyPair();
        int result = Sodium.generateKeyPair(mAliceKeys);
        L.i("Alice result: " + result);
        L.i(mAliceKeys.toString());

        mBobKeys = new KeyPair();
        result = Sodium.generateKeyPair(mBobKeys);
        L.i("Bob result: " + result);
        L.i(mBobKeys.toString());
    }

    public void onSecretKeyEncrypt(View v) {
        mSecretKeyEncryptedMessage = Sodium.secretKeyEncrypt("a secret message".getBytes(), mSecretKey);
        L.i(mSecretKeyEncryptedMessage.toString());
    }

    public void onSecretKeyDecrypt(View v) {
        byte[] msg = Sodium.secretKeyDecrypt(mSecretKeyEncryptedMessage.cipherText, mSecretKeyEncryptedMessage.nonce, mSecretKey);
        if (msg == null) {
            L.i("can't decrypt secret key message");
            return;
        }

        L.i("decrypted message: " + new String(msg));
    }

    public void onPublicKeyEncrypt(View v) {
        mPublicKeyEncryptedMessage = Sodium.publicKeyEncrypt(
                "something for someone".getBytes(),
                mBobKeys.publicKey,
                mAliceKeys.secretKey);
        if (mPublicKeyEncryptedMessage == null) {
            L.i("failed to encrypt message");
            return;
        }
        L.i("public key encrypted cipher text: " + mPublicKeyEncryptedMessage);
    }

    public void onPublicKeyDecrypt(View v) {
        byte[] msg = Sodium.publicKeyDecrypt(
                mPublicKeyEncryptedMessage.cipherText,
                mPublicKeyEncryptedMessage.nonce,
                mAliceKeys.publicKey,
                mBobKeys.secretKey);
        if (msg == null) {
            L.i("failed to decrypt message");
            return;
        }
        L.i("public key encrypted msg: " + new String(msg));
    }
}
