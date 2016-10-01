package io.pijun.george;

import android.content.Context;
import android.content.SharedPreferences;

import io.pijun.george.crypto.KeyPair;

public class Prefs {

    private static volatile Prefs sPrefs;
    private final SharedPreferences mPrefs;

    private final static String sKeySecretKey = "key_secret_key";
    private final static String sKeyPublicKey = "key_public_key";
    private final static String sKeyPasswordSalt = "key_password_salt";
    private final static String sKeySymmetricKey = "key_symmetric_key";
    private final static String sKeyAccessToken = "key_access_token";
    private final static String sKeyUserId = "key_user_id";

    private Prefs(Context context) {
        mPrefs = context.getSharedPreferences("secret.xml", Context.MODE_PRIVATE);
    }

    public static Prefs get(Context context) {
        if (sPrefs == null) {
            synchronized (Prefs.class) {
                if (sPrefs == null) {
                    sPrefs = new Prefs(context);
                }
            }
        }

        return sPrefs;
    }

    private byte[] getBytes(String key) {
        String hex = mPrefs.getString(key, null);
        if (hex == null) {
            return null;
        }
        return Hex.toBytes(hex);
    }

    private void setBytes(byte[] bytes, String key) {
        String hex = Hex.toHexString(bytes);
        mPrefs.edit().putString(key, hex).apply();
    }

    public KeyPair getKeyPair() {
        byte[] secKey, pubKey;
        secKey = getBytes(sKeySecretKey);
        pubKey = getBytes(sKeyPublicKey);

        if (secKey == null || pubKey == null) {
            return null;
        }

        KeyPair kp = new KeyPair();
        kp.secretKey = secKey;
        kp.publicKey = pubKey;
        return kp;
    }

    public void setKeyPair(KeyPair kp) {
        setBytes(kp.secretKey, sKeySecretKey);
        setBytes(kp.publicKey, sKeyPublicKey);
    }

    public byte[] getPasswordSalt() {
        return getBytes(sKeyPasswordSalt);
    }

    public void setPasswordSalt(byte[] salt) {
        setBytes(salt, sKeyPasswordSalt);
    }

    public byte[] getSymmetricKey() {
        return getBytes(sKeySymmetricKey);
    }

    public void setSymmetricKey(byte[] symKey) {
        setBytes(symKey, sKeySymmetricKey);
    }

    public String getAccessToken() {
        return mPrefs.getString(sKeyAccessToken, null);
    }

    public void setAccessToken(String token) {
        mPrefs.edit().putString(sKeyAccessToken, token).apply();
    }

    public byte[] getUserId() {
        return getBytes(sKeyUserId);
    }

    public void setUserId(byte[] id) {
        setBytes(id, sKeyUserId);
    }

}
