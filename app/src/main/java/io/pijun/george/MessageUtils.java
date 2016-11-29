package io.pijun.george;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.google.firebase.crash.FirebaseCrash;
import com.squareup.tape.FileObjectQueue;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.SecureRandom;

import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.api.task.MessageConverter;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRequested;
import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.RequestRecord;
import io.pijun.george.models.UserRecord;
import retrofit2.Response;

public class MessageUtils {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ERROR_NONE, ERROR_NOT_LOGGED_IN, ERROR_NO_NETWORK, ERROR_UNKNOWN_SENDER, ERROR_REMOTE_INTERNAL,
            ERROR_UNKNOWN, ERROR_INVALID_COMMUNICATION, ERROR_DATABASE_EXCEPTION, ERROR_INVALID_SENDER_ID,
            ERROR_MISSING_CIPHER_TEXT, ERROR_MISSING_NONCE, ERROR_NOT_A_FRIEND, ERROR_DECRYPTION_FAILED,
            ERROR_DATABASE_INCONSISTENCY
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

    @Error
    public static int approveFriendRequest(@NonNull Context context, long userId) {
        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(boxId);
        return approveFriendRequest(context, userId, boxId);
    }

    @Error
    public static int approveFriendRequest(@NonNull Context context, long userId, @NonNull byte[] boxId) {
        Prefs prefs = Prefs.get(context);
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            return ERROR_NOT_LOGGED_IN;
        }
        KeyPair kp = prefs.getKeyPair();
        if (kp == null) {
            return ERROR_NOT_LOGGED_IN;
        }

        UserComm comm = UserComm.newLocationSharingGrant(boxId);
        byte[] msgBytes = comm.toJSON();
        UserRecord user = DB.get(context).getUserById(userId);
        if (user == null) {
            throw new RuntimeException("How was approve called for an unknown user?");
        }
        EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, user.publicKey, kp.secretKey);
        OscarClient.queueSendMessage(context, accessToken, Hex.toHexString(user.userId), encMsg);

        try {
            DB.get(context).grantSharingTo(user.userId, boxId);
        } catch (DB.DBException ex) {
            L.w("serious problem setting drop box id", ex);
            FirebaseCrash.report(ex);
            return ERROR_DATABASE_EXCEPTION;
        }

        return ERROR_NONE;
    }

    @WorkerThread
    @Error
    public static int unwrapAndProcess(@NonNull Context context, @NonNull byte[] senderId, @NonNull byte[] cipherText, @NonNull byte[] nonce) {
        L.i("MessageUtils.unwrapAndProcess");
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
        if (!prefs.isLoggedIn()) {
            return ERROR_NOT_LOGGED_IN;
        }
        if (TextUtils.isEmpty(token) || keyPair == null) {
            return ERROR_NOT_LOGGED_IN;
        }

        if (userRecord == null) {
            L.i("|  need to download user");
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
                // now that we've encountered a new user, add them to the database (because of TOFU)

                userRecord = db.addUser(senderId, user.username, user.publicKey);
                L.i("|  added user: " + userRecord);
            } catch (IOException ioe) {
                return ERROR_NO_NETWORK;
            } catch (DB.DBException dbe) {
                FirebaseCrash.report(dbe);
                return ERROR_DATABASE_EXCEPTION;
            }
        }

        byte[] unwrappedBytes = Sodium.publicKeyDecrypt(cipherText, nonce, userRecord.publicKey, keyPair.secretKey);
        if (unwrappedBytes == null) {
            return ERROR_DECRYPTION_FAILED;
        }
        UserComm comm = UserComm.fromJSON(unwrappedBytes);
        if (!comm.isValid()) {
            L.i("usercomm was invalid. here it is: " + comm);
            return ERROR_INVALID_COMMUNICATION;
        }
        L.i("|  comm type: " + comm.type);
        switch (comm.type) {
            case LocationSharingGrant:
                L.i("LocationSharingGrant");
                try {
                    db.sharingGrantedBy(userRecord.username, comm.dropBox);
                } catch (DB.DBException ex) {
                    L.w("error recording location grant", ex);
                    FirebaseCrash.report(ex);
                    return ERROR_DATABASE_EXCEPTION;
                }
                App.postOnBus(new LocationSharingGranted(userRecord.id));
                break;
            case LocationSharingRequest:
                // If we're already sharing location with this person, just send them the drop box address
                FriendRecord friend = db.getFriendByUserId(userRecord.id);
                if (friend != null) {
                    // We already have them as a friend. Are we sharing our location with them?
                    if (friend.sendingBoxId != null) {
                        return approveFriendRequest(context, friend.userId, friend.sendingBoxId);
                    }
                }
                // We're not sharing with them, but let's see if we already have a request from them
                RequestRecord request = db.getIncomingRequestByUserId(userRecord.id);
                // TODO: If the request was rejected, resend the rejection message
                if (request != null) {
                    L.i("MessageUtils found a duplicate location request");
                    return ERROR_NONE;
                }
                try {
                    db.addIncomingRequest(userRecord.id, System.currentTimeMillis());
                } catch (DB.DBException ex) {
                    L.w("error recording sharing request", ex);
                    FirebaseCrash.report(ex);
                    return ERROR_DATABASE_EXCEPTION;
                }
                App.postOnBus(new LocationSharingRequested());
                break;
            case LocationSharingRejection:
                // TODO:
                break;
            case LocationInfo:
                FriendRecord fr = db.getFriendByUserId(userRecord.id);
                if (fr == null) {
                    // there should be a friend record for any locations that we receive
                    return ERROR_NOT_A_FRIEND;
                }
                try {
                    db.setFriendLocation(fr.id, comm.latitude, comm.longitude, comm.time, comm.accuracy, comm.speed);
                } catch (DB.DBException ex) {
                    L.w("error setting location info for friend", ex);
                    FirebaseCrash.report(ex);
                    return ERROR_DATABASE_EXCEPTION;
                }
                App.postOnBus(new FriendLocation(fr.id, comm));
                break;
        }

        return ERROR_NONE;
    }

}
