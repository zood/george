package xyz.zood.george;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collections;

import io.pijun.george.CloudLogger;
import io.pijun.george.Constants;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.service.BackupDatabaseJob;

public class FriendshipManager {

    @NonNull private final String accessToken;
    @NonNull private final Context ctx;
    @NonNull private final DB db;
    @NonNull private final KeyPair keyPair;

    @AnyThread
    FriendshipManager(@NonNull Context ctx, @NonNull DB db, @NonNull String accessToken, @NonNull KeyPair keyPair) {
        this.ctx = ctx;
        this.db = db;
        this.accessToken = accessToken;
        this.keyPair = keyPair;
    }

    @WorkerThread
    void addFriend(@NonNull String username, @NonNull OnAddFriendFinishedListener listener) {
        UserRecord user = db.getUser(username);
        if (user == null) {
            // the user should already have been added by the 'search as you type' feature
            listener.onAddFriendFinished(AddFriendResult.UserNotFound, null);
            return;
        }

        // check if we already have this user as a friend, and if we're already sharing with them
        final FriendRecord friend = db.getFriendByUserId(user.id);
        if (friend != null) {
            if (friend.sendingBoxId != null) {
                // send the sending box id to this person one more time, just in case
                UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                String errMsg = OscarClient.queueSendMessage(ctx, user, keyPair, accessToken, comm.toJSON(), false, false);
                if (errMsg != null) {
                    CloudLogger.log(errMsg);
                    listener.onAddFriendFinished(AddFriendResult.SharingGrantFailed, errMsg);
                } else {
                    listener.onAddFriendFinished(AddFriendResult.Success, null);
                }
                return;
            }
        }

        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
        String errMsg = OscarClient.queueSendMessage(ctx, user, keyPair, accessToken, comm.toJSON(), false, false);
        if (errMsg != null) {
            listener.onAddFriendFinished(AddFriendResult.SharingGrantFailed, errMsg);
            return;
        }

        try {
            db.startSharingWith(user, sendingBoxId);
        } catch (DB.DBException dbe) {
            listener.onAddFriendFinished(AddFriendResult.DatabaseError, dbe.getLocalizedMessage());
            CloudLogger.log(dbe);
            return;
        }

        try {
            AvatarManager.sendAvatarToUsers(ctx, Collections.singletonList(user), keyPair, accessToken);
        } catch (IOException ex) {
            CloudLogger.log(ex);
            // We're purposely not returning early here. This isn't a critical error.
        }

        listener.onAddFriendFinished(AddFriendResult.Success, null);
        BackupDatabaseJob.scheduleBackup(ctx);
    }

    @WorkerThread
    void removeFriend(@NonNull FriendRecord friend, @NonNull OnRemoveFriendFinishedListener listener) {
        stopSharingWith(friend, new OnShareOpFinishedListener() {
            @Override
            public void onShareOpFinished(boolean success) {
                if (!success) {
                    listener.onRemoveFriendFinished(false);
                }
                try {
                    db.removeFriend(friend);
                    listener.onRemoveFriendFinished(true);
                } catch (DB.DBException ex) {
                    CloudLogger.log(ex);
                    listener.onRemoveFriendFinished(false);
                }
            }
        });
    }

    @WorkerThread
    void startSharingWith(@NonNull FriendRecord friend, @NonNull OnShareOpFinishedListener listener) {
        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        // send the sending box id to the friend
        UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
        String errMsg = OscarClient.queueSendMessage(ctx, friend.user, comm, false, false);
        if (errMsg != null) {
            CloudLogger.log(new RuntimeException(errMsg));
            listener.onShareOpFinished(false);
            return;
        }

        // add this to our database
        try {
            db.startSharingWith(friend.user, sendingBoxId);
        } catch (DB.DBException ex) {
            CloudLogger.log(ex);
            listener.onShareOpFinished(false);
        }

        // send the friend our avatar
        try {
            AvatarManager.sendAvatarToUser(ctx, friend.user);
        } catch (IOException ex) {
            CloudLogger.log(ex);
            listener.onShareOpFinished(false);
        }
        BackupDatabaseJob.scheduleBackup(ctx);
    }

    @WorkerThread
    void stopSharingWith(@NonNull FriendRecord friend, @NonNull OnShareOpFinishedListener listener) {
        // remove the sending box id from the database
        try {
            db.stopSharingWith(friend.user);
        } catch (DB.DBException ex) {
            CloudLogger.log(ex);
            listener.onShareOpFinished(false);
        }
        BackupDatabaseJob.scheduleBackup(ctx);

        UserComm comm = UserComm.newLocationSharingRevocation();
        String errMsg = OscarClient.queueSendMessage(ctx, friend.user, comm, false, false);
        if (errMsg != null) {
            CloudLogger.log(new RuntimeException(errMsg));
            listener.onShareOpFinished(false);
        }
    }

    enum AddFriendResult {
        Success,
        UserNotFound,
        SharingGrantFailed,
        DatabaseError,
    }

    interface OnAddFriendFinishedListener {
        @WorkerThread
        void onAddFriendFinished(@NonNull AddFriendResult result, @Nullable String extraInfo);
    }

    interface OnShareOpFinishedListener {
        @WorkerThread
        void onShareOpFinished(boolean success);
    }

    interface OnRemoveFriendFinishedListener {
        @WorkerThread
        void onRemoveFriendFinished(boolean success);
    }

}