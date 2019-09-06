package io.pijun.george;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import java.security.SecureRandom;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.MovementType;

public class Prefs {

    private static volatile Prefs sPrefs;
    private final SharedPreferences mPrefs;

    private final static String KEY_SECRET_KEY = "key_secret_key";
    private final static String KEY_PUBLIC_KEY = "key_public_key";
    private final static String KEY_PASSWORD_SALT = "key_password_salt";
    private final static String KEY_SYMMETRIC_KEY = "key_symmetric_key";
    private final static String KEY_ACCESS_TOKEN = "key_access_token";
    private final static String KEY_USER_ID = "key_user_id";
    private final static String KEY_FCM_TOKEN = "fcm_token";
    private final static String KEY_USERNAME = "key_username";

    private final static String KEY_CAMERA_POSITION_SAVED = "camera_position_saved";
    private final static String KEY_CAMERA_POSITION_LATITUDE = "camera_position_latitude";
    private final static String KEY_CAMERA_POSITION_LONGITUDE = "camera_position_longitude";
    private final static String KEY_CAMERA_POSITION_BEARING = "camera_position_bearing";
    private final static String KEY_CAMERA_POSITION_TILT = "camera_position_tilt";
    private final static String KEY_CAMERA_POSITION_ZOOM = "camera_position_zoom";
    private final static String KEY_LAST_LOCATION_UPDATE_TIME = "last_location_update_time";
    private final static String KEY_CURRENT_MOVEMENT = "current_movement";

    private final static String KEY_DEVICE_ID = "device_id";

    private Prefs(Context context) {
        mPrefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE);
    }

    @NonNull
    public static Prefs get(@NonNull Context context) {
        if (sPrefs == null) {
            synchronized (Prefs.class) {
                if (sPrefs == null) {
                    sPrefs = new Prefs(context);
                }
            }
        }

        return sPrefs;
    }

    @AnyThread
    void clearAll() {
        mPrefs.edit().clear().apply();
    }

    @Nullable
    private byte[] getBytes(String key) {
        String hex = mPrefs.getString(key, null);
        if (hex == null) {
            return null;
        }
        return Hex.toBytes(hex);
    }

    private void setBytes(byte[] bytes, String key) {
        if (bytes != null) {
            String hex = Hex.toHexString(bytes);
            mPrefs.edit().putString(key, hex).apply();
        } else {
            mPrefs.edit().putString(key, null).apply();
        }
    }

    @Nullable
    public KeyPair getKeyPair() {
        byte[] secKey, pubKey;
        secKey = getBytes(KEY_SECRET_KEY);
        pubKey = getBytes(KEY_PUBLIC_KEY);

        if (secKey == null || pubKey == null) {
            return null;
        }

        KeyPair kp = new KeyPair();
        kp.secretKey = secKey;
        kp.publicKey = pubKey;
        return kp;
    }

    void setKeyPair(KeyPair kp) {
        if (kp != null) {
            setBytes(kp.secretKey, KEY_SECRET_KEY);
            setBytes(kp.publicKey, KEY_PUBLIC_KEY);
        } else {
            setBytes(null, KEY_SECRET_KEY);
            setBytes(null, KEY_PUBLIC_KEY);
        }
    }

    @Nullable
    byte[] getPasswordSalt() {
        return getBytes(KEY_PASSWORD_SALT);
    }

    void setPasswordSalt(byte[] salt) {
        setBytes(salt, KEY_PASSWORD_SALT);
    }

    @Nullable
    public byte[] getSymmetricKey() {
        return getBytes(KEY_SYMMETRIC_KEY);
    }

    void setSymmetricKey(byte[] symKey) {
        setBytes(symKey, KEY_SYMMETRIC_KEY);
    }

    @Nullable
    public String getAccessToken() {
        return mPrefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public void setAccessToken(@Nullable String token) {
        if (TextUtils.isEmpty(token)) {
            // so we never have to deal with checking for empty strings anywhere else in the app
            token = null;
        }
        mPrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply();
    }

    @Nullable
    public byte[] getUserId() {
        return getBytes(KEY_USER_ID);
    }

    public void setUserId(byte[] id) {
        setBytes(id, KEY_USER_ID);
    }

    public String getUsername() {
        return mPrefs.getString(KEY_USERNAME, null);
    }

    public void setUsername(String username) {
        mPrefs.edit().putString(KEY_USERNAME, username).apply();
    }

    @Nullable
    public String getFcmToken() {
        return mPrefs.getString(KEY_FCM_TOKEN, null);
    }

    public void setFcmToken(String token) {
        mPrefs.edit().putString(KEY_FCM_TOKEN, token).apply();
    }

    @Nullable
    public CameraPosition getCameraPosition() {
        if (!mPrefs.getBoolean(KEY_CAMERA_POSITION_SAVED, false)) {
            return null;
        }

        float bearing = 0;
        try {
            bearing = mPrefs.getFloat(KEY_CAMERA_POSITION_BEARING, 0);
        } catch (Throwable ignore) {}
        float tilt = 0;
        try {
            tilt = mPrefs.getFloat(KEY_CAMERA_POSITION_TILT, 0);
        } catch (Throwable ignore) {}
        float zoom = 0;
        try {
            zoom = mPrefs.getFloat(KEY_CAMERA_POSITION_ZOOM, 0);
        } catch (Throwable ignore) {}
        double lat = Double.longBitsToDouble(mPrefs.getLong(KEY_CAMERA_POSITION_LATITUDE, 0));
        double lng = Double.longBitsToDouble(mPrefs.getLong(KEY_CAMERA_POSITION_LONGITUDE, 0));

        LatLng ll = new LatLng(lat, lng);
        return new CameraPosition.Builder().bearing(bearing).target(ll).tilt(tilt).zoom(zoom).build();
    }

    public void setCameraPosition(@Nullable CameraPosition pos) {
        if (pos == null) {
            mPrefs.edit().putBoolean(KEY_CAMERA_POSITION_SAVED, false).apply();
            return;
        }

        mPrefs.edit()
                .putFloat(KEY_CAMERA_POSITION_BEARING, pos.bearing)
                .putFloat(KEY_CAMERA_POSITION_TILT, pos.tilt)
                .putFloat(KEY_CAMERA_POSITION_ZOOM, pos.zoom)
                .putLong(KEY_CAMERA_POSITION_LATITUDE, Double.doubleToRawLongBits(pos.target.latitude))
                .putLong(KEY_CAMERA_POSITION_LONGITUDE, Double.doubleToRawLongBits(pos.target.longitude))
                .putBoolean(KEY_CAMERA_POSITION_SAVED, true)
                .apply();
    }

    public long getLastLocationUpdateTime() {
        return mPrefs.getLong(KEY_LAST_LOCATION_UPDATE_TIME, 0);
    }

    void setLastLocationUpdateTime(long time) {
        mPrefs.edit().putLong(KEY_LAST_LOCATION_UPDATE_TIME, time).apply();
    }

    @NonNull
    public MovementType getCurrentMovement() {
        return MovementType.get(mPrefs.getString(KEY_CURRENT_MOVEMENT, null));
    }

    public void setCurrentMovement(@NonNull MovementType mvmt) {
        mPrefs.edit().putString(KEY_CURRENT_MOVEMENT, mvmt.val).apply();
    }

    @NonNull
    @CheckResult
    @Size(Constants.DEVICE_ID_SIZE)
    byte[] getDeviceId() {
        byte[] deviceId = getBytes(KEY_DEVICE_ID);
        if (deviceId == null) {
            deviceId = new byte[Constants.DEVICE_ID_SIZE];
            new SecureRandom().nextBytes(deviceId);
            setBytes(deviceId, KEY_DEVICE_ID);
        }

        return deviceId;
    }

}
