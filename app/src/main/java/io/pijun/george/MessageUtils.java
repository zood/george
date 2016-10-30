package io.pijun.george;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.io.IOException;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.FriendLocationUpdated;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRequested;
import io.pijun.george.models.FriendRecord;
import retrofit2.Response;

public class MessageUtils {

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

    @WorkerThread
    public static int unwrapAndProcess(@NonNull Context context, @NonNull byte[] senderId, @NonNull byte[] cipherText, @NonNull byte[] nonce) {
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
        FriendRecord fr = DB.get(context).getFriend(senderId);
        byte[] senderPubKey = null;
        String senderUsername = null;
        if (fr != null) {
            senderPubKey = fr.publicKey;
            senderUsername = fr.username;
        }
        Prefs prefs = Prefs.get(context);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (!prefs.isLoggedIn()) {
            return ERROR_NOT_LOGGED_IN;
        }

        if (senderPubKey == null) {
            // we need to retrieve it from the server
            OscarAPI api = OscarClient.newInstance(token);
            try {
                Response<User> response = api.getUser(Hex.toHexString(senderId)).execute();
                if (!response.isSuccessful()) {
                    OscarError apiErr = OscarError.fromResponse(response);
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
                senderPubKey = user.publicKey;
                senderUsername = user.username;
            } catch (IOException ex) {
                return ERROR_NO_NETWORK;
            }
        }

        byte[] unwrappedBytes = Sodium.publicKeyDecrypt(cipherText, nonce, senderPubKey, keyPair.secretKey);
        if (unwrappedBytes == null) {
            return ERROR_DECRYPTION_FAILED;
        }
        UserComm comm = UserComm.fromJSON(unwrappedBytes);
        if (!comm.isValid()) {
            L.i("usercomm was invalid. here is is: " + comm);
            return ERROR_INVALID_COMMUNICATION;
        }
        switch (comm.type) {
            case LocationSharingGrant:
                try {
                    // if we already have a record for this user, then add the receiving box id to our database
                    if (fr != null) {
                        DB.get(context).setReceivingDropBoxId(fr.username, comm.dropBox);
                    } else {
                        DB.get(context).addFriend(senderUsername, senderId, senderPubKey, null, comm.dropBox, false, null);
                    }
                } catch (DB.DBException ex) {
                    L.w("error recording location grant", ex);
                    return ERROR_DATABASE_EXCEPTION;
                }
                App.postOnBus(new LocationSharingGranted());
                break;
            case LocationSharingRequest:
                try {
                    if (fr != null) {
                        DB.get(context).setShareRequested(senderUsername, true);
                    } else {
                        DB.get(context).addFriend(senderUsername, senderId, senderPubKey, null, null, true, comm.note);
                    }
                } catch (DB.DBException ex) {
                    L.w("error recording sharing request", ex);
                    return ERROR_DATABASE_EXCEPTION;
                }
                App.postOnBus(new LocationSharingRequested());
                break;
            case LocationInfo:
                if (fr == null) {
                    // there should be a friend record for any locations that we receive
                    return ERROR_NOT_A_FRIEND;
                }
                try {
                    DB.get(context).setFriendLocation(fr.id, comm.latitude, comm.longitude, comm.time, comm.accuracy, comm.speed);
                } catch (DB.DBException ex) {
                    L.w("error setting location info for friend", ex);
                    return ERROR_DATABASE_EXCEPTION;
                }
                App.postOnBus(new FriendLocationUpdated());
                break;
        }

        return ERROR_NONE;
    }

}
