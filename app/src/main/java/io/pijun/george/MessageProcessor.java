package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;

import java.io.IOException;
import java.util.Arrays;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import io.pijun.george.api.DeviceInfo;
import io.pijun.george.api.LimitedUserInfo;
import io.pijun.george.api.Message;
import io.pijun.george.api.MessageConverter;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.OutboundMessage;
import io.pijun.george.api.UserComm;
import io.pijun.george.api.task.SendMessageTask;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.queue.PersistentQueue;
import io.pijun.george.service.BackupDatabaseJob;
import io.pijun.george.service.PositionService;
import retrofit2.Response;
import xyz.zood.george.service.ScreamerService;

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
    private static volatile MessageProcessor singleton;
    private final PersistentQueue<Message> queue;

    private MessageProcessor() {
        queue = new PersistentQueue<>(App.getApp(), QUEUE_FILENAME, new MessageConverter());
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

        // check if this message is from ourself
        Prefs prefs = Prefs.get(context);
        byte[] ourId = prefs.getUserId();
        if (ourId == null) {
            return Result.ErrorNotLoggedIn;
        }
        if (Arrays.equals(senderId, ourId)) {
            return handleMessageFromOurself(context, cipherText, nonce, ourId);
        }


        DB db = DB.get();
        UserRecord user = db.getUser(senderId);
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
                user = db.addUser(senderId, lui.username, lui.publicKey);
                L.i("  added user: " + user);
                BackupDatabaseJob.scheduleBackup(context);
            } catch (IOException ioe) {
                return Result.ErrorNoNetwork;
            } catch (DB.DBException dbe) {
                CloudLogger.log(dbe);
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
            case BrowseDevices:
                L.i("We shouldn't be receiving browse_devices from other users");
                return Result.ErrorInvalidCommunication;
            case Debug:
                L.i("debug from " + user.username + ": " + comm.debugData);
                return Result.Success;
            case DeviceInfo:
                L.i("We shouldn't be receiving device_info from other users");
                return Result.ErrorInvalidCommunication;
            case LocationSharingGrant:
                return handleLocationSharingGrant(context, user, comm);
            case LocationSharingRevocation:
                return handleLocationSharingRevocation(context, user);
            case LocationInfo:
                return handleLocationInfo(user, comm);
            case LocationUpdateRequest:
                return handleLocationUpdateRequest(context, user, token, keyPair);
            case LocationUpdateRequestReceived:
                return handleLocationUpdateRequestReceived(user, comm);
            case Scream:
            default:
                L.i("The invalid comm should have been caught during the isValid() check: " +
                        user.username + " - " + comm);
                return Result.ErrorInvalidCommunication;
        }
    }

    @NonNull
    @AnyThread
    private static MessageProcessor get() {
        if (singleton == null) {
            synchronized (MessageProcessor.class) {
                if (singleton == null) {
                    singleton = new MessageProcessor();
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            singleton.processQueue();
                        }
                    });
                }
            }
        }
        return singleton;
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
            CloudLogger.log(ex);
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
            CloudLogger.log(ex);
        }
        return Result.Success;
    }

    private static Result handleBrowseDevices(@NonNull Context ctx, @NonNull String accessToken, @NonNull KeyPair keyPair, @NonNull @Size(Constants.USER_ID_LENGTH) byte[] ourId) {
        DeviceInfo di = new DeviceInfo(Prefs.get(ctx).getDeviceId(),
                Build.MANUFACTURER,
                Build.MODEL,
                "Android",
                Build.VERSION.RELEASE);
        UserComm c = UserComm.newDeviceInfo(di);
        // make sure we have everything we need
        EncryptedData encrypted = Sodium.publicKeyEncrypt(c.toJSON(), keyPair.publicKey, keyPair.secretKey);
        if (encrypted == null) {
            L.w("Failed to encrypt device info message to myself");
            return Result.ErrorEncryptionFailed;
        }
        OutboundMessage om = new OutboundMessage();
        om.cipherText = encrypted.cipherText;
        om.nonce = encrypted.nonce;
        om.urgent = true;
        om.isTransient = true;

        OscarAPI api = OscarClient.newInstance(accessToken);
        try {
            Response<Void> response = api.sendMessage(Hex.toHexString(ourId), om).execute();
            if (response.isSuccessful()) {
                return Result.Success;
            }

            OscarError err = OscarError.fromResponse(response);
            if (err != null) {
                L.w(err.toString());
            } else {
                L.w("Unknown server error received while sending device_info");
            }
        } catch (IOException ex) {
            L.i("Network error while trying to send device_info");
        }

        // something happened, so we'll queue it to try again later.
        SendMessageTask smt = new SendMessageTask(accessToken);
        smt.hexUserId = Hex.toHexString(ourId);
        smt.message = encrypted;
        smt.urgent = true;
        smt.isTransient = true;
        OscarClient.getQueue(ctx).offer(smt);

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
            db.setFriendLocation(fr.id, comm.latitude, comm.longitude, comm.time, comm.accuracy, comm.speed, comm.bearing, comm.movement, comm.batteryLevel);
        } catch (DB.DBException ex) {
            L.w("error setting location info for friend", ex);
            CloudLogger.log(ex);
            return Result.ErrorDatabaseException;
        }

        return Result.Success;
    }

    private static Result handleLocationSharingGrant(@NonNull Context context, @NonNull UserRecord user, @NonNull UserComm comm) {
        L.i("LocationSharingGrant");
        DB db = DB.get();
        try {
            db.sharingGrantedBy(user, comm.dropBox);
            BackupDatabaseJob.scheduleBackup(context);
        } catch (DB.DBException ex) {
            L.w("error recording location grant", ex);
            CloudLogger.log(ex);
            return Result.ErrorDatabaseException;
        }

        return Result.Success;
    }

    private static Result handleLocationSharingRevocation(@NonNull Context context, @NonNull UserRecord user) {
        L.i("LocationSharingRevocation");
        DB db = DB.get();
        db.sharingRevokedBy(user);
        BackupDatabaseJob.scheduleBackup(context);
        return Result.Success;
    }

    private static Result handleLocationUpdateRequest(@NonNull Context context, @NonNull UserRecord userRecord, @NonNull String accessToken, @NonNull KeyPair keyPair) {
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
            String errMsg = OscarClient.immediatelySendMessage(userRecord, accessToken, keyPair, tooSoon, true, true);
            if (errMsg != null) {
                L.w(errMsg);
            }
            return Result.Success;
        }

        L.i("MU attempting to start the position service");
        ContextCompat.startForegroundService(context, PositionService.newIntent(context, userRecord.id));

        // let them know we've started
        L.i("queueing update starting");
        UserComm started = UserComm.newLocationUpdateRequestReceived(UserComm.LOCATION_UPDATE_REQUEST_ACTION_STARTING);
        String errMsg = OscarClient.immediatelySendMessage(userRecord, accessToken, keyPair, started, true, true);
        if (errMsg != null) {
            L.w(errMsg);
        }

        return Result.Success;
    }

    private static Result handleLocationUpdateRequestReceived(@NonNull UserRecord user, @NonNull UserComm comm) {
        String logMsg = String.format("handleLocationUpdateRequestReceived - %s - %s", user.username, comm.locationUpdateRequestAction);
        L.i(logMsg);
        FriendRecord friend = DB.get().getFriendByUserId(user.id);
        if (friend == null || friend.receivingBoxId == null) {
            return Result.Success;
        }
        UpdateStatusTracker.setUpdateRequestResponse(friend.id, System.currentTimeMillis(), comm.locationUpdateRequestAction);

        return Result.Success;
    }

    private static Result handleMessageFromOurself(@NonNull Context context, @NonNull byte[] cipherText, @NonNull byte[] nonce, @NonNull @Size(Constants.USER_ID_LENGTH) byte[] ourId) {
        if (!AuthenticationManager.isLoggedIn(context)) {
            return Result.ErrorNotLoggedIn;
        }

        Prefs prefs = Prefs.get(context);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            return Result.ErrorNotLoggedIn;
        }

        byte[] userCommBytes = Sodium.publicKeyDecrypt(cipherText, nonce, keyPair.publicKey, keyPair.secretKey);
        if (userCommBytes == null) {
            return Result.ErrorDecryptionFailed;
        }
        UserComm comm = UserComm.fromJSON(userCommBytes);
        if (!comm.isValid()) {
            L.i("usercomm from myself was invalid. here it is: " + comm);
            return Result.ErrorInvalidCommunication;
        }

        // we only support a few messages from ourself
        switch (comm.type) {
            case BrowseDevices:
                return handleBrowseDevices(context, token, keyPair, ourId);
            case DeviceInfo:
                // We don't care. Only useful for the web client
                return Result.Success;
            case Scream:
                return handleScreamRequest(context, token, keyPair, ourId);
            default:
                return Result.ErrorInvalidCommunication;
        }
    }

    private static Result handleScreamRequest(@NonNull Context context, @NonNull String accessToken, @NonNull KeyPair keyPair, @NonNull @Size(Constants.USER_ID_LENGTH) byte[] ourId) {
        Intent i = ScreamerService.newStartIntent(context);
        ContextCompat.startForegroundService(context, i);

        UserComm c = UserComm.newScreamBegan();
        // make sure we have everything we need
        EncryptedData encrypted = Sodium.publicKeyEncrypt(c.toJSON(), keyPair.publicKey, keyPair.secretKey);
        if (encrypted == null) {
            L.w("Failed to encrypt device info message to myself");
            return Result.ErrorEncryptionFailed;
        }
        OutboundMessage om = new OutboundMessage();
        om.cipherText = encrypted.cipherText;
        om.nonce = encrypted.nonce;
        om.urgent = true;
        om.isTransient = true;

        OscarAPI api = OscarClient.newInstance(accessToken);
        try {
            Response<Void> response = api.sendMessage(Hex.toHexString(ourId), om).execute();
            if (response.isSuccessful()) {
                return Result.Success;
            }

            OscarError err = OscarError.fromResponse(response);
            if (err != null) {
                L.w(err.toString());
            } else {
                L.w("Unknown server error received while sending scream_began");
            }
        } catch (IOException ex) {
            L.i("Network error while trying to send scream_began");
        }

        // something happened, so we'll queue it to try again later.
        SendMessageTask smt = new SendMessageTask(accessToken);
        smt.hexUserId = Hex.toHexString(ourId);
        smt.message = encrypted;
        smt.urgent = true;
        smt.isTransient = true;
        OscarClient.getQueue(context).offer(smt);

        return Result.Success;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @WorkerThread
    private void processQueue() {
        while (true) {
            Message msg;
            try {
                msg = queue.blockingPeek();
                Result result = decryptAndProcess(App.getApp(), msg.senderId, msg.cipherText, msg.nonce);
                String token = Prefs.get(App.getApp()).getAccessToken();
                switch (result) {
                    case Success:
                        queue.poll();
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
                        queue.poll();
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
                CloudLogger.log(t);
            }
        }
    }

    @AnyThread
    public static void queue(@NonNull Message msg) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                get().queue.offer(msg);
            }
        });
    }

}
