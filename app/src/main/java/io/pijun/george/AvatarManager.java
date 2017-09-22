package io.pijun.george;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.AnyThread;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.AvatarUpdated;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.UserRecord;

class AvatarManager {

    private static final String AVATAR_DIR = "avatars";
    private static final String MY_AVATAR = "me";

    @CheckResult @NonNull @AnyThread
    static File getAvatar(@NonNull Context ctx, @NonNull String username) {
        File filesDir = ctx.getFilesDir();
        File avatarsDir = new File(filesDir, AVATAR_DIR);
        return new File(avatarsDir, username.toLowerCase(Locale.US) + ".jpg");
    }

    @CheckResult @NonNull @AnyThread
    static File getMyAvatar(@NonNull Context ctx) {
        return getAvatar(ctx, MY_AVATAR);
    }

    @WorkerThread @CheckResult
    static boolean saveAvatar(@NonNull Context ctx, @NonNull String username, @NonNull byte[] imgData) throws IOException {
        L.i("AvatarManager.saveAvatar for " + username);
        username = username.toLowerCase(Locale.US);
        File filesDir = ctx.getFilesDir();
        File avatarsDir = new File(filesDir, AVATAR_DIR);
        if (!avatarsDir.exists()) {
            boolean success = avatarsDir.mkdir();
            if (!success) {
                FirebaseCrash.logcat(Log.ERROR, L.TAG, "Unable to create the avatars directory");
                return false;
            }
        }
        // make sure this image can be decoded
        if (BitmapFactory.decodeByteArray(imgData, 0, imgData.length) == null) {
            L.w("Unable to decode bitmap from " + username);
            return false;
        }

        File imgFile = new File(avatarsDir, username + ".jpg");
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imgFile);
        } catch (FileNotFoundException ex) {
            FirebaseCrash.report(ex);
            return false;
        }

        fos.write(imgData);
        fos.close();

        Picasso.with(ctx).invalidate(getAvatar(ctx, username));
        App.postOnBus(new AvatarUpdated(username));
        return true;
    }

    @WorkerThread
    @CheckResult
    static boolean setMyAvatar(@NonNull Context ctx, @NonNull Bitmap img) throws IOException {
        File filesDir = ctx.getFilesDir();
        File avatarsDir = new File(filesDir, AVATAR_DIR);
        if (!avatarsDir.exists()) {
            boolean success = avatarsDir.mkdir();
            if (!success) {
                FirebaseCrash.logcat(Log.ERROR, L.TAG, "Unable to create the avatars directory to save personal avatar");
                return false;
            }
        }
        File imgFile = new File(avatarsDir, MY_AVATAR + ".jpg");
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imgFile);
        } catch (FileNotFoundException ex) {
            FirebaseCrash.report(ex);
            return false;
        }
        boolean success = img.compress(Bitmap.CompressFormat.JPEG, 80, fos);
        if (!success) {
            FirebaseCrash.logcat(Log.ERROR, L.TAG, "Failed to compress avatar image to file");
            return false;
        }
        fos.flush();
        fos.close();
        Picasso.with(ctx).invalidate(getMyAvatar(ctx));

        App.postOnBus(new AvatarUpdated(null));

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    sendAvatarToFriends();
                } catch (IOException ex) {
                    FirebaseCrash.report(ex);
                }
            }
        });

        return true;
    }

    @WorkerThread
    static void sendAvatarToUser(@NonNull Context ctx, @NonNull UserRecord user) throws IOException {
        File avatarFile = getMyAvatar(ctx);
        if (!avatarFile.exists()) {
            return;
        }
        Prefs prefs = Prefs.get(ctx);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("AvatarManager.sendAvatarToUser missing token or keypair");
            return;
        }
        FileInputStream fis = new FileInputStream(avatarFile);
        byte []buffer = new byte[(int) avatarFile.length()];
        int read = fis.read(buffer);
        if (read < avatarFile.length()) {
            L.w("Did not read entire avatar image");
            return;
        }
        UserComm comm = UserComm.newAvatarUpdate(buffer);
        byte[] json = comm.toJSON();
        EncryptedData encMsg = Sodium.publicKeyEncrypt(json, user.publicKey, keyPair.secretKey);
        if (encMsg == null) {
            L.i("Encrypting avatar for " + user.username + " failed");
            return;
        }
        L.i("Sending avatar to " + user.username);
        String errMsg = OscarClient.queueSendMessage(ctx, user, comm, false, false);
        if (errMsg != null) {
            L.w(errMsg);
        }
    }

    @WorkerThread
    private static void sendAvatarToFriends() throws IOException {
        Context ctx = App.getApp();
        File avatarFile = getMyAvatar(ctx);
        if (!avatarFile.exists()) {
            return;
        }
        ArrayList<FriendRecord> friends = DB.get(ctx).getFriendsToShareWith();
        if (friends.size() == 0) {
            return;
        }
        Prefs prefs = Prefs.get(ctx);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("AvatarManager.sendAvatarToFriends missing token or keyPair");
            return;
        }

        FileInputStream fis = new FileInputStream(avatarFile);
        byte []buffer = new byte[(int) avatarFile.length()];
        int read = fis.read(buffer);
        if (read < avatarFile.length()) {
            L.w("Did not read entire avatar image");
            return;
        }
        UserComm comm = UserComm.newAvatarUpdate(buffer);
        byte[] json = comm.toJSON();
        for (FriendRecord f : friends) {
            L.i("Sending avatar to " + f.user.username);
            String errMsg = OscarClient.queueSendMessage(ctx, f.user, json, false, false);
            if (errMsg != null) {
                L.w("Problem sending avatar: " + errMsg);
            }
        }
    }

}
