package io.pijun.george;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.service.BackupDatabaseJob;

public class AvatarManager {

    private static final String AVATAR_DIR = "avatars";
    public static final String MY_AVATAR = "me";
    private static CopyOnWriteArrayList<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();

    //region Listener management

    @AnyThread
    static void addListener(@NonNull Listener listener) {
        WeakReference<Listener> ref = new WeakReference<>(listener);
        listeners.add(ref);
    }

    @AnyThread
    private static void notifyListeners(@Nullable String username) {
        // Copy the items into another list, so we don't block any add/remove operations while
        // notifying listeners.
        LinkedList<WeakReference<Listener>> copy = new LinkedList<>(listeners);
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : copy) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onAvatarUpdated(username);
                }
            }
        });
    }

    @AnyThread
    static void removeListener(@NonNull Listener listener) {
        int i=0;
        while (i < listeners.size()) {
            WeakReference<Listener> ref = listeners.get(i);
            Listener l = ref.get();
            if (l == null || l == listener) {
                listeners.remove(i);
                continue;
            }
            i++;
        }
    }

    //endregion

    @WorkerThread
    static void deleteAll(@NonNull Context ctx) {
        File filesDir = ctx.getFilesDir();
        File avatarsDir = new File(filesDir, AVATAR_DIR);
        File[] avatars = avatarsDir.listFiles();
        if (avatars == null) {
            return;
        }

        for (File a : avatars) {
            //noinspection ResultOfMethodCallIgnored
            a.delete();
        }
    }

    @CheckResult
    @NonNull @AnyThread
    static File getAvatar(@NonNull Context ctx, @NonNull String username) {
        File filesDir = ctx.getFilesDir();
        File avatarsDir = new File(filesDir, AVATAR_DIR);
        return new File(avatarsDir, username.toLowerCase(Locale.US) + ".jpg");
    }

    @CheckResult @NonNull @AnyThread
    public static File getMyAvatar(@NonNull Context ctx) {
        return getAvatar(ctx, MY_AVATAR);
    }

    @WorkerThread @CheckResult
    public static boolean saveAvatar(@NonNull Context ctx, @NonNull String username, @NonNull byte[] imgData) throws IOException {
        L.i("AvatarManager.saveAvatar for " + username);
        username = username.toLowerCase(Locale.US);
        File filesDir = ctx.getFilesDir();
        File avatarsDir = new File(filesDir, AVATAR_DIR);
        if (!avatarsDir.exists()) {
            boolean success = avatarsDir.mkdir();
            if (!success) {
                L.w("Unable to create the avatars directory");
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
            CloudLogger.log(ex);
            return false;
        }

        fos.write(imgData);
        fos.close();

        Picasso.with(ctx).invalidate(getAvatar(ctx, username));
        notifyListeners(username);
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
                L.w("Unable to create the avatars directory to save personal avatar");
                return false;
            }
        }
        File imgFile = new File(avatarsDir, MY_AVATAR + ".jpg");
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imgFile);
        } catch (FileNotFoundException ex) {
            CloudLogger.log(ex);
            return false;
        }
        boolean success = img.compress(Bitmap.CompressFormat.JPEG, 80, fos);
        if (!success) {
            L.w("Failed to compress avatar image to file");
            return false;
        }
        fos.flush();
        fos.close();
        Picasso.with(ctx).invalidate(getMyAvatar(ctx));

        notifyListeners(null);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    ArrayList<FriendRecord> friends = DB.get().getFriendsToShareWith();
                    LinkedList<UserRecord> users = new LinkedList<>();
                    for (FriendRecord f : friends) {
                        users.add(f.user);
                    }
                    sendAvatarToUsers(ctx, users);
                    BackupDatabaseJob.scheduleBackup(ctx);
                } catch (IOException ex) {
                    CloudLogger.log(ex);
                }
            }
        });

        return true;
    }

    @WorkerThread
    static void sendAvatarToUser(@NonNull Context ctx, @NonNull UserRecord user) throws IOException {
        sendAvatarToUsers(ctx, Collections.singletonList(user));
    }

    @WorkerThread
    private static void sendAvatarToUsers(@NonNull Context ctx, @NonNull List<UserRecord> users) throws IOException {
        StringBuilder sb = new StringBuilder("sendAvatarToUsers: ");
        for (UserRecord u : users) {
            sb.append(u.username);
            sb.append(",");
        }
        L.i(sb.toString());

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
        for (UserRecord u : users) {
            String errMsg = OscarClient.queueSendMessage(ctx, u, comm, false, false);
            if (errMsg != null) {
                L.w(errMsg);
            }
        }
    }

    interface Listener {
        @UiThread
        void onAvatarUpdated(@Nullable String username);
    }

}
