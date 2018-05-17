package io.pijun.george;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.crashlytics.android.Crashlytics;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.service.PositionService;
import retrofit2.Response;

public class MessageUtils {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ERROR_NONE, ERROR_NOT_LOGGED_IN, ERROR_NO_NETWORK, ERROR_UNKNOWN_SENDER, ERROR_REMOTE_INTERNAL,
            ERROR_UNKNOWN, ERROR_INVALID_COMMUNICATION, ERROR_DATABASE_EXCEPTION, ERROR_INVALID_SENDER_ID,
            ERROR_MISSING_CIPHER_TEXT, ERROR_MISSING_NONCE, ERROR_NOT_A_FRIEND, ERROR_DECRYPTION_FAILED,
            ERROR_DATABASE_INCONSISTENCY, ERROR_ENCRYPTION_FAILED
    })
    public @interface Error {}

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NOT_LOGGED_IN = 1;
    public static final int ERROR_NO_NETWORK = 2;
    public static final int ERROR_UNKNOWN_SENDER = 3;
    public static final int ERROR_REMOTE_INTERNAL = 4;
    public static final int ERROR_UNKNOWN = 5;
    public static final int ERROR_INVALID_COMMUNICATION = 6;
    public static final int ERROR_DATABASE_EXCEPTION = 7;
    public static final int ERROR_INVALID_SENDER_ID = 8;
    public static final int ERROR_MISSING_CIPHER_TEXT = 9;
    public static final int ERROR_MISSING_NONCE = 10;
    public static final int ERROR_NOT_A_FRIEND = 11;
    public static final int ERROR_DECRYPTION_FAILED = 12;
    public static final int ERROR_DATABASE_INCONSISTENCY = 13;
    public static final int ERROR_ENCRYPTION_FAILED = 14;

    @WorkerThread @Error
    private static int handleAvatarRequest(@NonNull Context ctx, @NonNull UserRecord user) {
        L.i("handleAvatarRequest: " + user.username);
        try {
            // make sure this is somebody that we're sharing our location with
            FriendRecord friend = DB.get(ctx).getFriendByUserId(user.id);
            L.i("\tfriendrecord: " + friend);
            if (friend == null) {
                L.i("\tnot a friend");
                return ERROR_NONE;
            }
            if (friend.sendingBoxId == null) {
                L.i("\tno sending box id");
                return ERROR_NONE;
            }
            AvatarManager.sendAvatarToUser(ctx, user);
        } catch (IOException ex) {
            Crashlytics.logException(ex);
            L.w("Error handling avatar request", ex);
        }

        return ERROR_NONE;
    }

    @WorkerThread @Error
    private static int handleAvatarUpdate(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        try {
            boolean success = AvatarManager.saveAvatar(context, user.username, comm.avatar);
            if (!success) {
                L.w("Failed to save avatar from " + user.username);
            }
        } catch (IOException ex) {
            Crashlytics.logException(ex);
        }
        return ERROR_NONE;
    }

    @WorkerThread @Error
    private static int handleLocationInfo(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        DB db = DB.get(context);
        FriendRecord fr = db.getFriendByUserId(user.id);
        if (fr == null) {
            // there should be a friend record for any locations that we receive
            return ERROR_NOT_A_FRIEND;
        }
        try {
            db.setFriendLocation(fr.id, comm.latitude, comm.longitude, comm.time, comm.accuracy, comm.speed, comm.bearing);
        } catch (DB.DBException ex) {
            L.w("error setting location info for friend", ex);
            Crashlytics.logException(ex);
            return ERROR_DATABASE_EXCEPTION;
        }
        App.postOnBus(new FriendLocation(fr.id, comm));

        return ERROR_NONE;
    }

    @WorkerThread @Error
    private static int handleLocationSharingGrant(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        L.i("LocationSharingGrant");
        DB db = DB.get(context);
        try {
            db.sharingGrantedBy(user, comm.dropBox);
        } catch (DB.DBException ex) {
            L.w("error recording location grant", ex);
            Crashlytics.logException(ex);
            return ERROR_DATABASE_EXCEPTION;
        }
        App.postOnBus(new LocationSharingGranted(user.id));

        return ERROR_NONE;
    }

    @WorkerThread @Error
    private static int handleLocationSharingRevocation(@NonNull Context context, @NonNull UserRecord user) {
        L.i("LocationSharingRevocation");
        DB db = DB.get(context);
        db.sharingRevokedBy(user);
        App.postOnBus(new LocationSharingRevoked(user.id));
        return ERROR_NONE;
    }

    @WorkerThread @Error
    private static int handleLocationUpdateRequest(@NonNull Context context, @NonNull UserRecord userRecord) {
        L.i("handleLocationUpdateRequest from " + userRecord.username);
        Prefs prefs = Prefs.get(context);
        long updateTime = prefs.getLastLocationUpdateTime();

        // make sure this is actually a friend
        FriendRecord f = DB.get(context).getFriendByUserId(userRecord.id);
        if (f == null || f.sendingBoxId == null) {
            return ERROR_NONE;
        }

        // Only perform the update if it's been more than 3 minutes since the last one
        // and the app isn't in the foreground
        long timeSinceUpdate = System.currentTimeMillis() - updateTime;
        if (!App.isInForeground && timeSinceUpdate < 3 * DateUtils.MINUTE_IN_MILLIS) {
            L.i("\talready provided an update at " + updateTime);
            UserComm tooSoon = UserComm.newLocationUpdateRequestReceived(UserComm.LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON);
            String errMsg = OscarClient.queueSendMessage(context, userRecord, tooSoon, true, true);
            if (errMsg != null) {
                L.w(errMsg);
            }
            return ERROR_NONE;
        }

        L.i("MU attempting to start the position service");
        ContextCompat.startForegroundService(context, PositionService.newIntent(context));
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                PositionService.await();
                UserComm done = UserComm.newLocationUpdateRequestReceived(UserComm.LOCATION_UPDATE_REQUEST_ACTION_FINISHED);
                L.i("Queueing 'location_update_request_action_finished' to " + userRecord.username);
                String errMsg = OscarClient.queueSendMessage(context, userRecord, done, true, true);
                if (errMsg != null) {
                    L.w(errMsg);
                }
            }
        });
        UserComm started = UserComm.newLocationUpdateRequestReceived(UserComm.LOCATION_UPDATE_REQUEST_ACTION_STARTING);
        String errMsg = OscarClient.queueSendMessage(context, userRecord, started, true, true);
        if (errMsg != null) {
            L.w(errMsg);
        }

        return ERROR_NONE;
    }

    @WorkerThread @Error
    private static int handleLocationUpdateRequestReceived(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        L.i("handleLocationUpdateRequestReceived");
        L.i(user.username + " responded to update request: " + comm.locationUpdateRequestAction);
        FriendRecord friend = DB.get(context).getFriendByUserId(user.id);
        if (friend == null) {
            return ERROR_NONE;
        }
        UpdateStatusTracker.setUpdateRequestResponse(friend.id, System.currentTimeMillis(), comm.locationUpdateRequestAction);

        return ERROR_NONE;
    }

    @WorkerThread @Error
    public static int unwrapAndProcess(@NonNull Context context, @NonNull byte[] senderId, @NonNull byte[] cipherText, @NonNull byte[] nonce) {
//        L.i("MessageUtils.unwrapAndProcess");
        //noinspection ConstantConditions
        if (senderId == null || senderId.length != Constants.USER_ID_LENGTH) {
            L.i("senderId: " + Hex.toHexString(senderId));
            return ERROR_INVALID_SENDER_ID;
        }
        //noinspection ConstantConditions
        if (cipherText == null) {
            return ERROR_MISSING_CIPHER_TEXT;
        }
        //noinspection ConstantConditions
        if (nonce == null) {
            return ERROR_MISSING_NONCE;
        }
        DB db = DB.get(context);
        UserRecord userRecord = db.getUser(senderId);
        Prefs prefs = Prefs.get(context);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (!AuthenticationManager.isLoggedIn(context)) {
            return ERROR_NOT_LOGGED_IN;
        }
        if (TextUtils.isEmpty(token) || keyPair == null) {
            return ERROR_NOT_LOGGED_IN;
        }

        if (userRecord == null) {
            L.i("  need to download user");
            // we need to retrieve it from the server
            OscarAPI api = OscarClient.newInstance(token);
            try {
                Response<User> response = api.getUser(Hex.toHexString(senderId)).execute();
                if (!response.isSuccessful()) {
                    OscarError apiErr = OscarError.fromResponse(response);
                    if (apiErr == null) {
                        return ERROR_UNKNOWN;
                    }
                    switch (apiErr.code) {
                        case OscarError.ERROR_INVALID_ACCESS_TOKEN:
                            return ERROR_NOT_LOGGED_IN;
                        case OscarError.ERROR_USER_NOT_FOUND:
                            return ERROR_UNKNOWN_SENDER;
                        case OscarError.ERROR_INTERNAL:
                            return ERROR_REMOTE_INTERNAL;
                        default:
                            return ERROR_UNKNOWN;
                    }
                }
                User user = response.body();
                if (user == null) {
                    L.w("Unable to decode user " + Hex.toHexString(senderId) + " from response");
                    return ERROR_UNKNOWN;
                }
                // now that we've encountered a new user, add them to the database (because of TOFU)
                userRecord = db.addUser(senderId, user.username, user.publicKey);
                L.i("  added user: " + userRecord);
            } catch (IOException ioe) {
                return ERROR_NO_NETWORK;
            } catch (DB.DBException dbe) {
                Crashlytics.logException(dbe);
                return ERROR_DATABASE_EXCEPTION;
            }
        }

        byte[] unwrappedBytes = Sodium.publicKeyDecrypt(cipherText, nonce, userRecord.publicKey, keyPair.secretKey);
        if (unwrappedBytes == null) {
            return ERROR_DECRYPTION_FAILED;
        }
        UserComm comm = UserComm.fromJSON(unwrappedBytes);
        if (!comm.isValid()) {
            L.i("usercomm from " + userRecord.username + " was invalid. here it is: " + comm);
            return ERROR_INVALID_COMMUNICATION;
        }
//        L.i("  comm type: " + comm.type);
        switch (comm.type) {
            case AvatarRequest:
                return handleAvatarRequest(context, userRecord);
            case AvatarUpdate:
                return handleAvatarUpdate(context, userRecord, comm);
            case Debug:
                L.i("debug from " + userRecord.username + ": " + comm.debugData);
                return ERROR_NONE;
            case LocationSharingGrant:
                return handleLocationSharingGrant(context, userRecord, comm);
            case LocationSharingRevocation:
                return handleLocationSharingRevocation(context, userRecord);
            case LocationInfo:
                return handleLocationInfo(context, userRecord, comm);
            case LocationUpdateRequest:
                return handleLocationUpdateRequest(context, userRecord);
            case LocationUpdateRequestReceived:
                return handleLocationUpdateRequestReceived(context, userRecord, comm);
            default:
                L.i("The invalid comm should have been caught during the isValid() check: " +
                        userRecord.username + " - " + comm);
                return ERROR_INVALID_COMMUNICATION;
        }
    }

}
