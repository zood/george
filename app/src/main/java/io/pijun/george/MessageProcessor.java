package io.pijun.george;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.crashlytics.android.Crashlytics;

import java.io.IOException;

import io.pijun.george.api.LimitedUserInfo;
import io.pijun.george.api.Message;
import io.pijun.george.api.MessageConverter;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
import io.pijun.george.queue.PersistentQueue;
import io.pijun.george.service.PositionService;
import retrofit2.Response;

public class MessageProcessor {

    public enum Result {
        Success,
        ErrorDatabaseException,
        ErrorDatabaseInconsistency,
        ErrorDecryptionFailed,
        ErrorEncryptionFailed,
        ErrorInvalidCommunication,
        ErrorInvalidSenderId,
        ErrorMissingCipherText,
        ErrorMissingNonce,
        ErrorNoNetwork,
        ErrorNotAFriend,
        ErrorNotLoggedIn,
        ErrorRemoteInternal,
        ErrorUnknown,
        ErrorUnknownSender
    }

    private static final String QUEUE_FILENAME = "message_processor";
    private static volatile MessageProcessor sSingleton;
    private final PersistentQueue<Message> mQueue;

    private MessageProcessor() {
        mQueue = new PersistentQueue<>(App.getApp(), QUEUE_FILENAME, new MessageConverter());
    }

    public static Result decryptAndProcess(@NonNull Context context, @NonNull byte[] senderId, @NonNull byte[] cipherText, @NonNull byte[] nonce) {
//        L.i("MessageUtils.decryptAndProcess");
        //noinspection ConstantConditions
        if (senderId == null || senderId.length != Constants.USER_ID_LENGTH) {
            L.i("senderId: " + Hex.toHexString(senderId));
            return Result.ErrorInvalidSenderId;
        }
        //noinspection ConstantConditions
        if (cipherText == null) {
            return Result.ErrorMissingCipherText;
        }
        //noinspection ConstantConditions
        if (nonce == null) {
            return Result.ErrorMissingNonce;
        }
        DB db = DB.get();
        UserRecord user = db.getUser(senderId);
        Prefs prefs = Prefs.get(context);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (!AuthenticationManager.isLoggedIn(context)) {
            return Result.ErrorNotLoggedIn;
        }
        if (TextUtils.isEmpty(token) || keyPair == null) {
            return Result.ErrorNotLoggedIn;
        }

        if (user == null) {
            L.i("  need to download user");
            // we need to retrieve it from the server
            OscarAPI api = OscarClient.newInstance(token);
            try {
                Response<LimitedUserInfo> response = api.getUser(Hex.toHexString(senderId)).execute();
                if (!response.isSuccessful()) {
                    OscarError apiErr = OscarError.fromResponse(response);
                    if (apiErr == null) {
                        return Result.ErrorUnknown;
                    }
                    switch (apiErr.code) {
                        case OscarError.ERROR_INVALID_ACCESS_TOKEN:
                            return Result.ErrorNotLoggedIn;
                        case OscarError.ERROR_USER_NOT_FOUND:
                            return Result.ErrorUnknownSender;
                        case OscarError.ERROR_INTERNAL:
                            return Result.ErrorRemoteInternal;
                        default:
                            return Result.ErrorUnknown;
                    }
                }
                LimitedUserInfo lui = response.body();
                if (lui == null) {
                    L.w("Unable to decode user " + Hex.toHexString(senderId) + " from response");
                    return Result.ErrorUnknown;
                }
                // now that we've encountered a new user, add them to the database (because of TOFU)
                user = db.addUser(senderId, lui.username, lui.publicKey, true, context);
                L.i("  added user: " + user);
            } catch (IOException ioe) {
                return Result.ErrorNoNetwork;
            } catch (DB.DBException dbe) {
                Crashlytics.logException(dbe);
                return Result.ErrorDatabaseException;
            }
        }

        byte[] unwrappedBytes = Sodium.publicKeyDecrypt(cipherText, nonce, user.publicKey, keyPair.secretKey);
        if (unwrappedBytes == null) {
            return Result.ErrorDecryptionFailed;
        }
        UserComm comm = UserComm.fromJSON(unwrappedBytes);
        if (!comm.isValid()) {
            L.i("usercomm from " + user.username + " was invalid. here it is: " + comm);
            return Result.ErrorInvalidCommunication;
        }
//        L.i("  comm type: " + comm.type);
        switch (comm.type) {
            case AvatarRequest:
                return handleAvatarRequest(context, user);
            case AvatarUpdate:
                return handleAvatarUpdate(context, user, comm);
            case Debug:
                L.i("debug from " + user.username + ": " + comm.debugData);
                return Result.Success;
            case LocationSharingGrant:
                return handleLocationSharingGrant(context, user, comm);
            case LocationSharingRevocation:
                return handleLocationSharingRevocation(context, user);
            case LocationInfo:
                return handleLocationInfo(user, comm);
            case LocationUpdateRequest:
                return handleLocationUpdateRequest(context, user);
            case LocationUpdateRequestReceived:
                return handleLocationUpdateRequestReceived(user, comm);
            default:
                L.i("The invalid comm should have been caught during the isValid() check: " +
                        user.username + " - " + comm);
                return Result.ErrorInvalidCommunication;
        }
    }

    @NonNull
    @AnyThread
    public static MessageProcessor get() {
        if (sSingleton == null) {
            synchronized (MessageProcessor.class) {
                if (sSingleton == null) {
                    sSingleton = new MessageProcessor();
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            sSingleton.processQueue();
                        }
                    });
                }
            }
        }
        return sSingleton;
    }

    private static Result handleAvatarRequest(@NonNull Context ctx, @NonNull UserRecord user) {
        L.i("handleAvatarRequest: " + user.username);
        try {
            // make sure this is somebody that we're sharing our location with
            FriendRecord friend = DB.get().getFriendByUserId(user.id);
            L.i("\tfriendrecord: " + friend);
            if (friend == null) {
                L.i("\tnot a friend");
                return Result.Success;
            }
            if (friend.sendingBoxId == null) {
                L.i("\tno sending box id");
                return Result.Success;
            }
            AvatarManager.sendAvatarToUser(ctx, user);
        } catch (IOException ex) {
            Crashlytics.logException(ex);
            L.w("Error handling avatar request", ex);
        }

        return Result.Success;
    }

    private static Result handleAvatarUpdate(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        L.i("handleAvatarUpdate for " + user.username);
        try {
            // make sure this person is at least a friend
            if (DB.get().getFriendByUserId(user.id) == null) {
                L.i(user.username + " is not a friend. We don't want their avatar.");
                return Result.Success;
            }

            boolean success = AvatarManager.saveAvatar(context, user.username, comm.avatar);
            if (!success) {
                L.w("Failed to save avatar from " + user.username);
            }
        } catch (IOException ex) {
            L.w("Exception saving avatar", ex);
            Crashlytics.logException(ex);
        }
        return Result.Success;
    }

    private static Result handleLocationInfo(@NonNull UserRecord user, @NonNull UserComm comm) {
        DB db = DB.get();
        FriendRecord fr = db.getFriendByUserId(user.id);
        if (fr == null) {
            // there should be a friend record for any locations that we receive
            return Result.ErrorNotAFriend;
        }
        try {
            db.setFriendLocation(fr.id, comm.latitude, comm.longitude, comm.time, comm.accuracy, comm.speed, comm.bearing);
        } catch (DB.DBException ex) {
            L.w("error setting location info for friend", ex);
            Crashlytics.logException(ex);
            return Result.ErrorDatabaseException;
        }
        App.postOnBus(new FriendLocation(fr.id, comm));

        return Result.Success;
    }

    private static Result handleLocationSharingGrant(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        L.i("LocationSharingGrant");
        DB db = DB.get();
        try {
            db.sharingGrantedBy(user, comm.dropBox, context);
        } catch (DB.DBException ex) {
            L.w("error recording location grant", ex);
            Crashlytics.logException(ex);
            return Result.ErrorDatabaseException;
        }
        App.postOnBus(new LocationSharingGranted(user.id));

        return Result.Success;
    }

    private static Result handleLocationSharingRevocation(@NonNull Context context, @NonNull UserRecord user) {
        L.i("LocationSharingRevocation");
        DB db = DB.get();
        db.sharingRevokedBy(user, context);
        App.postOnBus(new LocationSharingRevoked(user.id));
        return Result.Success;
    }

    private static Result handleLocationUpdateRequest(@NonNull Context context, @NonNull UserRecord userRecord) {
        L.i("handleLocationUpdateRequest from " + userRecord.username);
        Prefs prefs = Prefs.get(context);
        long updateTime = prefs.getLastLocationUpdateTime();

        // make sure this is actually a friend
        FriendRecord f = DB.get().getFriendByUserId(userRecord.id);
        if (f == null || f.sendingBoxId == null) {
            return Result.Success;
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
            return Result.Success;
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

        return Result.Success;
    }

    private static Result handleLocationUpdateRequestReceived(@NonNull UserRecord user, @NonNull UserComm comm) {
        L.i("handleLocationUpdateRequestReceived");
        L.i(user.username + " responded to update request: " + comm.locationUpdateRequestAction);
        FriendRecord friend = DB.get().getFriendByUserId(user.id);
        if (friend == null || friend.receivingBoxId == null) {
            return Result.Success;
        }
        UpdateStatusTracker.setUpdateRequestResponse(friend.id, System.currentTimeMillis(), comm.locationUpdateRequestAction);

        return Result.Success;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @WorkerThread
    private void processQueue() {
        while (true) {
            Message msg;
            try {
                msg = mQueue.blockingPeek();
                Result result = decryptAndProcess(App.getApp(), msg.senderId, msg.cipherText, msg.nonce);
                String token = Prefs.get(App.getApp()).getAccessToken();
                switch (result) {
                    case Success:
                        mQueue.poll();
                        // delete the action from the server
                        if (msg.id != 0 && !TextUtils.isEmpty(token)) {
                            OscarClient.queueDeleteMessage(App.getApp(), token, msg.id);
                        }
                        break;
                    case ErrorNoNetwork:
                    case ErrorRemoteInternal:
                        L.w("reschedulable action processing error");
                        // sleep for 60 seconds, then try again
                        Thread.sleep(60 * DateUtils.SECOND_IN_MILLIS);
                        break;
                    case ErrorDecryptionFailed:
                        // somebody must have a corrupt keypair
                    case ErrorInvalidSenderId:
                    case ErrorMissingCipherText:
                    case ErrorMissingNonce:
                    case ErrorInvalidCommunication:
                        // just remove the invalid message
                    case ErrorNotLoggedIn:
                        // if we're not logged in, toss the message
                    case ErrorDatabaseException:
                    case ErrorDatabaseInconsistency:
                    case ErrorEncryptionFailed:
                    case ErrorNotAFriend:
                    case ErrorUnknownSender:
                    case ErrorUnknown:
                    default:
                        mQueue.poll();
                        // delete the action from the server
                        if (msg.id != 0 && !TextUtils.isEmpty(token)) {
                            OscarClient.queueDeleteMessage(App.getApp(), token, msg.id);
                        }
                        L.w("error processing action: " + result);
                        UserRecord user = DB.get().getUser(msg.senderId);
                        if (user != null) {
                            L.w("\tfrom " + user.username);
                        } else {
                            L.w("\tfrom an unknown user");
                        }
                        break;
                }
            } catch (Throwable t) {
                L.w("MessageProcessor.processQueue exception", t);
                Crashlytics.logException(t);
            }
        }
    }

    @AnyThread
    public void queue(@NonNull Message msg) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                mQueue.offer(msg);
            }
        });
    }

}
