package io.pijun.george.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.util.LongSparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import io.pijun.george.App;
import xyz.zood.george.AvatarManager;
import io.pijun.george.CloudLogger;
import io.pijun.george.Constants;
import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;

public class DB {

    private static final String FRIENDS_TABLE = "friends";
    private static final String FRIENDS_COL_ID = "id";
    private static final String FRIENDS_COL_USER_ID = "user_id";
    private static final String FRIENDS_COL_SENDING_BOX_ID = "sending_box_id";
    private static final String FRIENDS_COL_RECEIVING_BOX_ID = "receiving_box_id";
    private static final String[] FRIENDS_COLUMNS = new String[]{
            FRIENDS_COL_ID,
            FRIENDS_COL_USER_ID,
            FRIENDS_COL_SENDING_BOX_ID,
            FRIENDS_COL_RECEIVING_BOX_ID
    };

    private static final String LIMITED_SHARES_TABLE = "limited_shares";
    private static final String LIMITED_SHARES_COL_ID = "id";
    private static final String LIMITED_SHARES_COL_SENDING_BOX_ID = "sending_box_id";
    private static final String LIMITED_SHARES_COL_PUBLIC_KEY = "public_key";
    private static final String[] LIMITED_SHARES_COLUMNS = new String[]{
            LIMITED_SHARES_COL_ID,
            LIMITED_SHARES_COL_SENDING_BOX_ID,
            LIMITED_SHARES_COL_PUBLIC_KEY
    };

    private static final String LOCATIONS_TABLE = "locations";
    private static final String LOCATIONS_COL_FRIEND_ID = "friend_id";
    private static final String LOCATIONS_COL_LATITUDE = "latitude";
    private static final String LOCATIONS_COL_LONGITUDE = "longitude";
    private static final String LOCATIONS_COL_TIME = "time";
    private static final String LOCATIONS_COL_ACCURACY = "accuracy";
    private static final String LOCATIONS_COL_SPEED = "speed";
    private static final String LOCATIONS_COL_BEARING = "bearing";
    private static final String LOCATIONS_COL_MOVEMENT = "movement";
    private static final String LOCATIONS_COL_BATTERY_LEVEL = "battery_level";
    private static final String LOCATIONS_COL_BATTERY_CHARGING = "battery_charging";
    private static final String[] LOCATIONS_COLUMNS = new String[]{
            LOCATIONS_COL_FRIEND_ID,
            LOCATIONS_COL_LATITUDE,
            LOCATIONS_COL_LONGITUDE,
            LOCATIONS_COL_TIME,
            LOCATIONS_COL_ACCURACY,
            LOCATIONS_COL_SPEED,
            LOCATIONS_COL_BEARING,
            LOCATIONS_COL_MOVEMENT,
            LOCATIONS_COL_BATTERY_LEVEL,
            LOCATIONS_COL_BATTERY_CHARGING
    };

    private static final String USERS_TABLE = "users";
    private static final String USERS_COL_ID = "id";
    private static final String USERS_COL_USER_ID = "user_id";
    private static final String USERS_COL_USERNAME = "username";
    private static final String USERS_COL_PUBLIC_KEY = "public_key";
    private static final String[] USERS_COLUMNS = new String[]{
            USERS_COL_ID,
            USERS_COL_USER_ID,
            USERS_COL_USERNAME,
            USERS_COL_PUBLIC_KEY
    };

    private static DB sDb = new DB();

    public static class DBException extends Exception {
        DBException(String msg) {
            super(msg);
        }

        DBException(String msg, Throwable t) {
            super(msg, t);
        }
    }

    private DBHelper mDbHelper;
    private CopyOnWriteArrayList<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();

    @WorkerThread
    private DB() {}

    @NonNull
    @AnyThread
    public static DB get() {
        if (sDb.mDbHelper == null) {
            throw new RuntimeException("You have to call DB.init before using the DB");
        }

        return sDb;
    }

    public static void init(@NonNull Context context, boolean inMemory) {
        sDb.mDbHelper = new DBHelper(context, inMemory ? null : "thedata2");
    }

    @WorkerThread
    private void addFriend(long userId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FRIENDS_COL_USER_ID, userId);
        cv.put(FRIENDS_COL_SENDING_BOX_ID, sendingBoxId);
        cv.put(FRIENDS_COL_RECEIVING_BOX_ID, receivingBoxId);
        long result;
        try {
            result = db.insertOrThrow(FRIENDS_TABLE, null, cv);
        } catch (SQLException ex) {
            throw new DBException("Error creating friend " + userId, ex);
        }

        if (result == -1) {
            throw new DBException("Unknown database error while adding friend (-1)");
        }
    }

    @WorkerThread
    public void addLimitedShare(@NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId) throws DBException {
        // to make sure we always have just one at a time, wipe the database before proceeding
        deleteLimitedShares();

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(LIMITED_SHARES_COL_PUBLIC_KEY, publicKey);
        cv.put(LIMITED_SHARES_COL_SENDING_BOX_ID, sendingBoxId);
        long result;
        try {
            result = db.insertOrThrow(LIMITED_SHARES_TABLE, null, cv);
        } catch (SQLException ex) {
            throw new DBException("Error adding limited share {pubKey:"+ Hex.toHexString(publicKey) + ", sendingBoxId:"+Hex.toHexString(sendingBoxId) + "}", ex);
        }

        if (result == -1) {
            throw new DBException("Unknown database error adding limited share (-1)");
        }

        // NOTE: We don't bother scheduling a backup here because this data is (purposely) not
        // included in a snapshot.
    }

    @WorkerThread
    @NonNull
    public UserRecord addUser(@NonNull @Size(Constants.USER_ID_LENGTH) final byte[] userId,
                              @NonNull String username,
                              @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(USERS_COL_USER_ID, userId);
        cv.put(USERS_COL_USERNAME, username);
        cv.put(USERS_COL_PUBLIC_KEY, publicKey);
        long result;
        try {
            result = db.insertOrThrow(USERS_TABLE, null, cv);
        } catch (SQLException ex) {
            throw new DBException("Error inserting user " + username, ex);
        }

        UserRecord user = new UserRecord();
        user.id = result;
        user.userId = userId;
        user.username = username;
        user.publicKey = publicKey;

        return user;
    }

    @WorkerThread
    public void deleteLimitedShares() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.delete(LIMITED_SHARES_TABLE, null, null);
    }

    @WorkerThread
    public void deleteAllData() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.delete(FRIENDS_TABLE, null, null);
        db.delete(LOCATIONS_TABLE, null, null);
        db.delete(USERS_TABLE, null, null);
        db.delete(LIMITED_SHARES_TABLE, null, null);
    }

    @WorkerThread
    @Nullable
    public FriendRecord getFriendById(long friendId) {
        FriendRecord friend = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = FRIENDS_COL_ID + "=?";
        String[] selectionArgs = new String[]{
                String.valueOf(friendId)
        };
        try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, selectionArgs, null, null, null)) {
            if (c.moveToNext()) {
                friend = readFriend(c);
                friend.user = getUser(friend.userId);
            }
        }
        return friend;
    }

    @WorkerThread
    @Nullable
    @CheckResult
    public FriendRecord getFriendByReceivingBoxId(@NonNull @Size(Constants.DROP_BOX_ID_LENGTH) final byte[] boxId) {
        return getFriendMatchingBlob(boxId, FRIENDS_COL_RECEIVING_BOX_ID);
    }

    @WorkerThread
    @Nullable
    @CheckResult
    public FriendRecord getFriendByUserId(long userId) {
        FriendRecord friend = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = FRIENDS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(userId)};
        try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, args, null, null, null)) {
            if (c.moveToNext()) {
                friend = readFriend(c);
                friend.user = getUser(userId);
            }
        }

        return friend;
    }

    @Nullable
    @CheckResult
    public FriendLocation getFriendLocation(long friendRecordId) {
        FriendLocation fl = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = LOCATIONS_COL_FRIEND_ID + "=?";
        String[] selectionArgs = new String[]{String.valueOf(friendRecordId)};
        try (Cursor c = db.query(LOCATIONS_TABLE, LOCATIONS_COLUMNS, selection, selectionArgs, null, null, null)) {
            if (c.moveToNext()) {
                double lat = c.getDouble(c.getColumnIndexOrThrow(LOCATIONS_COL_LATITUDE));
                double lng = c.getDouble(c.getColumnIndexOrThrow(LOCATIONS_COL_LONGITUDE));
                long time = c.getLong(c.getColumnIndexOrThrow(LOCATIONS_COL_TIME));
                Float acc = null;
                int accColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_ACCURACY);
                if (!c.isNull(accColIdx)) {
                    acc = c.getFloat(accColIdx);
                }
                Float speed = null;
                int speedColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_SPEED);
                if (!c.isNull(speedColIdx)) {
                    speed = c.getFloat(speedColIdx);
                }
                Float bearing = null;
                int bearingColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_BEARING);
                if (!c.isNull(bearingColIdx)) {
                    bearing = c.getFloat(bearingColIdx);
                }
                int movementsColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_MOVEMENT);
                MovementType movement = MovementType.get(c.getString(movementsColIdx));
                Integer batteryLevel = null;
                int batteryLevelIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_BATTERY_LEVEL);
                if (!c.isNull(batteryLevelIdx)) {
                    batteryLevel = c.getInt(batteryLevelIdx);
                }
                Boolean batteryCharging = null;
                int batteryChargingIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_BATTERY_CHARGING);
                if (!c.isNull(batteryChargingIdx)) {
                    batteryCharging = (c.getInt(batteryChargingIdx) > 0);
                }
                fl = new FriendLocation(friendRecordId, lat, lng, time, acc, speed, bearing, movement, batteryLevel, batteryCharging);
            }
        }

        return fl;
    }

    @WorkerThread
    @Nullable
    @CheckResult
    private FriendRecord getFriendMatchingBlob(@NonNull final byte[] blob, @NonNull String matchingColumn) {
        StringBuilder sql = new StringBuilder("SELECT ");
        String delim = "";
        for (String col : FRIENDS_COLUMNS) {
            sql.append(delim).append(col);
            delim = ",";
        }
        sql.append(" FROM ").append(FRIENDS_TABLE).append(" WHERE ").append(matchingColumn).append("=?");
        SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
            @Override
            public Cursor newCursor(SQLiteDatabase sqLiteDatabase, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
                query.bindBlob(1, blob);
                return new SQLiteCursor(driver, editTable, query);
            }
        };
        FriendRecord fr = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor c = db.rawQueryWithFactory(factory, sql.toString(), null, FRIENDS_TABLE)) {
            if (c.moveToNext()) {
                fr = readFriend(c);
                // obtain the UserRecord for this friend
                fr.user = getUser(fr.userId);
            }
        }

        return fr;
    }

    @WorkerThread
    @NonNull
    @CheckResult
    public ArrayList<FriendRecord> getFriends() {
        ArrayList<FriendRecord> records = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                FriendRecord fr = readFriend(cursor);
                fr.user = getUser(fr.userId);
                records.add(fr);
            }
        }

        return records;
    }

    @WorkerThread
    @NonNull
    @CheckResult
    public ArrayList<FriendRecord> getFriendsToShareWith() {
        ArrayList<FriendRecord> records = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = FRIENDS_COL_SENDING_BOX_ID + " IS NOT NULL";
        try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, null, null, null, null)) {
            while (cursor.moveToNext()) {
                FriendRecord fr = readFriend(cursor);
                fr.user = getUser(fr.userId);
                records.add(fr);
            }
        }

        return records;
    }

    @WorkerThread
    @Nullable
    @CheckResult
    public LimitedShare getLimitedShare() {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor c = db.query(LIMITED_SHARES_TABLE, LIMITED_SHARES_COLUMNS, null, null, null, null, null)) {
            if (c.moveToNext()) {
                return readLimitedShare(c);
            }
        }

        return null;
    }

    @WorkerThread
    @CheckResult
    public int getSchemaVersion() {
        return mDbHelper.getReadableDatabase().getVersion();
    }

    @WorkerThread
    @NonNull
    @CheckResult
    public Snapshot getSnapshot() {
        Snapshot snapshot = new Snapshot();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        db.beginTransaction();
        try {
            LongSparseArray<byte[]> localToGlobalId = new LongSparseArray<>();
            try (Cursor c = db.query(USERS_TABLE, USERS_COLUMNS, null, null, null, null, null)) {
                while (c.moveToNext()) {
                    UserRecord userRecord = readUser(c);
                    Snapshot.User u = new Snapshot.User();
                    localToGlobalId.put(userRecord.id, userRecord.userId);
                    u.userId = userRecord.userId;
                    u.publicKey = userRecord.publicKey;
                    u.username= userRecord.username;
                    snapshot.users.add(u);
                }
            }
            try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, null, null, null, null, null)) {
                while (c.moveToNext()) {
                    FriendRecord friendRecord = readFriend(c);
                    Snapshot.Friend f = new Snapshot.Friend();
                    friendRecord.user = getUser(friendRecord.userId);
                    if (friendRecord.user == null) {
                        CloudLogger.log("unable to find a user record for a friend");
                        continue;
                    }
                    f.userId = friendRecord.user.userId;
                    f.sendingBoxId = friendRecord.sendingBoxId;
                    f.receivingBoxId = friendRecord.receivingBoxId;
                    snapshot.friends.add(f);
                }
            }

            snapshot.schemaVersion = db.getVersion();
            snapshot.timestamp = System.currentTimeMillis();

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        File avatar = AvatarManager.getMyAvatar(App.getApp());
        try {
            FileInputStream fis = new FileInputStream(avatar);
            byte[] buffer = new byte[(int)avatar.length()];
            int read = fis.read(buffer);
            if (read == avatar.length()) {
                snapshot.avatar = buffer;
            }
        } catch (FileNotFoundException fnfe) {
            L.w("No avatar found");
        } catch (IOException ioe) {
            L.w("Unable to read avatar data", ioe);
        }

        return snapshot;
    }

    @WorkerThread
    @Nullable
    @CheckResult
    public UserRecord getUser(@NonNull final byte[] id) {
        StringBuilder sql = new StringBuilder("SELECT ");
        String delim = "";
        for (String col : USERS_COLUMNS) {
            sql.append(delim).append(col);
            delim = ",";
        }
        sql.append(" FROM ").append(USERS_TABLE).append(" WHERE ").append(USERS_COL_USER_ID).append("=?");
        SQLiteDatabase.CursorFactory factory = (sqLiteDatabase, driver, editTable, query) -> {
            query.bindBlob(1, id);
            return new SQLiteCursor(driver, editTable, query);
        };
        UserRecord ur = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor c = db.rawQueryWithFactory(factory, sql.toString(), null, USERS_TABLE)) {
            if (c.moveToNext()) {
                ur = readUser(c);
            }
        }

        return ur;
    }

    @WorkerThread
    @Nullable
    @CheckResult
    public UserRecord getUser(@NonNull String username) {
        username = username.toLowerCase(Locale.US);
        UserRecord ur = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = USERS_COL_USERNAME + "=?";
        String[] args = new String[]{username};
        try (Cursor c = db.query(USERS_TABLE, USERS_COLUMNS, selection, args, null, null, null)) {
            if (c.moveToNext()) {
                ur = readUser(c);
            }
        }

        return ur;
    }

    @WorkerThread
    @Nullable
    @CheckResult
    public UserRecord getUser(long id) {
        UserRecord ur = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = USERS_COL_ID + "=?";
        String[] args = new String[]{String.valueOf(id)};
        try (Cursor c = db.query(USERS_TABLE, USERS_COLUMNS, selection, args, null, null, null)) {
            if (c.moveToNext()) {
                ur = readUser(c);
            }
        }

        return ur;
    }

    @WorkerThread
    @NonNull
    @CheckResult
    private static FriendRecord readFriend(Cursor c) {
        FriendRecord f = new FriendRecord();
        f.id = c.getLong(c.getColumnIndexOrThrow(FRIENDS_COL_ID));
        f.userId = c.getLong(c.getColumnIndexOrThrow(FRIENDS_COL_USER_ID));
        f.sendingBoxId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_SENDING_BOX_ID));
        f.receivingBoxId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_RECEIVING_BOX_ID));

        return f;
    }

    @WorkerThread
    @NonNull
    @CheckResult
    private static LimitedShare readLimitedShare(Cursor c) {
        LimitedShare ls = new LimitedShare();
        ls.id = c.getLong(c.getColumnIndexOrThrow(LIMITED_SHARES_COL_ID));
        ls.publicKey = c.getBlob(c.getColumnIndexOrThrow(LIMITED_SHARES_COL_PUBLIC_KEY));
        ls.sendingBoxId = c.getBlob(c.getColumnIndexOrThrow(LIMITED_SHARES_COL_SENDING_BOX_ID));

        return ls;
    }

    @WorkerThread
    @NonNull
    @CheckResult
    private static UserRecord readUser(Cursor c) {
        UserRecord ur = new UserRecord();
        ur.id = c.getLong(c.getColumnIndexOrThrow(USERS_COL_ID));
        ur.publicKey = c.getBlob(c.getColumnIndexOrThrow(USERS_COL_PUBLIC_KEY));
        ur.userId = c.getBlob(c.getColumnIndexOrThrow(USERS_COL_USER_ID));
        ur.username = c.getString(c.getColumnIndexOrThrow(USERS_COL_USERNAME));
        return ur;
    }

    @WorkerThread
    public void removeFriend(@NonNull FriendRecord friend) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int result;
        try {
            String[] args = new String[]{String.valueOf(friend.user.id)};
            result = db.delete(FRIENDS_TABLE, FRIENDS_COL_USER_ID + "=?", args);
        } catch (SQLException ex) {
            String msg = String.format(Locale.US, "Error removing friend %d (%s)", friend.id, friend.user.username);
            throw new DBException(msg, ex);
        }

        if (result != 1) {
            String msg = String.format(Locale.US, "%d rows affected when attempting to remove 1 friend. Id: %d, Username: %s", result, friend.id, friend.user.username);
            throw new DBException(msg);
        }

        notifyFriendRemoved(friend.id);
    }

    @WorkerThread
    public void restoreDatabase(@NonNull Context ctx, @NonNull Snapshot snapshot) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (snapshot.schemaVersion > db.getVersion()) {
            throw new DBException("Snapshot is from a newer database format");
        }

        try {
            db.beginTransaction();
            deleteAllData();

            for (Snapshot.User u : snapshot.users) {
                addUser(u.userId, u.username, u.publicKey);
            }
            for (Snapshot.Friend f : snapshot.friends) {
                UserRecord user = getUser(f.userId);
                if (user == null) {
                    throw new DBException("Found friend without corresponding user");
                }
                addFriend(user.id, f.sendingBoxId, f.receivingBoxId);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (snapshot.avatar != null && snapshot.avatar.length > 0) {
            try {
                // We can use this method, because we don't need to notify our friends about an avatar change
                boolean success = AvatarManager.saveAvatar(ctx, AvatarManager.MY_AVATAR, snapshot.avatar);
                if (!success) {
                    L.w("Failed to restore avatar from backup");
                }
            } catch (IOException ioe) {
                L.w("Failed to restore avatar to disk", ioe);
            }
        }
    }

    @WorkerThread
    public void setFriendLocation(long friendId, double lat, double lng, long time, @Nullable Float accuracy, @Nullable Float speed, @Nullable Float bearing, @Nullable String movement, @Nullable Integer batteryLevel, @Nullable Boolean batteryCharging) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(LOCATIONS_COL_FRIEND_ID, friendId);
        cv.put(LOCATIONS_COL_LATITUDE, lat);
        cv.put(LOCATIONS_COL_LONGITUDE, lng);
        cv.put(LOCATIONS_COL_TIME, time);
        if (accuracy != null) {
            cv.put(LOCATIONS_COL_ACCURACY, accuracy);
        }
        if (speed != null) {
            cv.put(LOCATIONS_COL_SPEED, speed);
        }
        if (bearing != null) {
            cv.put(LOCATIONS_COL_BEARING, bearing);
        }
        if (movement != null && !movement.equals(MovementType.Unknown.val)) {
            cv.put(LOCATIONS_COL_MOVEMENT, movement);
        }
        if (batteryLevel != null) {
            cv.put(LOCATIONS_COL_BATTERY_LEVEL, batteryLevel);
        }
        if (batteryCharging != null) {
            cv.put(LOCATIONS_COL_BATTERY_CHARGING, batteryCharging);
        }
        long result = db.replace(LOCATIONS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error occurred while setting friend location");
        }

        notifyFriendLocationUpdated(new FriendLocation(friendId, lat, lng, time, accuracy, speed, bearing, MovementType.get(movement), batteryLevel, batteryCharging));
    }

    @WorkerThread
    public void sharingGrantedBy(@NonNull UserRecord user,
                                 @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
        FriendRecord friend = getFriendByUserId(user.id);
        if (friend == null) {
            // add a friend record including the drop box id
            addFriend(user.id, null, boxId);
        } else {
            // add the drop box id to the existing friend record
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_RECEIVING_BOX_ID, boxId);
            String selection = FRIENDS_COL_USER_ID + "=?";
            String[] args = new String[]{String.valueOf(user.id)};
            long result = db.update(FRIENDS_TABLE, cv, selection, args);
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + user.username + "'");
            }
        }

        notifySharingGranted(user.id);
    }

    @WorkerThread
    public void sharingRevokedBy(@NonNull UserRecord user) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FRIENDS_COL_RECEIVING_BOX_ID, (byte[]) null);
        String whereClause = FRIENDS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(user.id)};
        db.update(FRIENDS_TABLE, cv, whereClause, args);

        notifySharingRevoked(user.id);
    }

    @WorkerThread
    public void startSharingWith(@NonNull UserRecord user, @NonNull byte[] sendingBoxId) throws DBException {
        FriendRecord fr = getFriendByUserId(user.id);
        if (fr == null) {
            addFriend(user.id, sendingBoxId, null);
        } else {
            // if we already have a friend record, just add the sending box id
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SENDING_BOX_ID, sendingBoxId);
            String selection = FRIENDS_COL_USER_ID + "=?";
            String[] args = new String[]{String.valueOf(user.id)};
            long result = db.update(FRIENDS_TABLE, cv, selection, args);
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + user.username + "'");
            }
        }

        notifyStartedSharingWithUser(user.id);
    }

    @WorkerThread
    public void stopSharingWith(@NonNull UserRecord user) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FRIENDS_COL_SENDING_BOX_ID, (byte[]) null);
        String whereClause = FRIENDS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(user.id)};
        long result = db.update(FRIENDS_TABLE, cv, whereClause, args);
        if (result != 1) {
            throw new DBException("Num affected rows was " + result + " for username '" + user.username + "'");
        }

        notifyStoppedSharingWithUser(user.id);
    }

    private static class DBHelper extends SQLiteOpenHelper {

        private DBHelper(@NonNull Context context, @Nullable String dbName) {
            super(context, dbName, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String createFriends = "CREATE TABLE "
                    + FRIENDS_TABLE + " ("
                    + FRIENDS_COL_ID + " INTEGER PRIMARY KEY, "
                    + FRIENDS_COL_USER_ID + " INTEGER UNIQUE NOT NULL, "
                    + FRIENDS_COL_SENDING_BOX_ID + " BLOB, "
                    + FRIENDS_COL_RECEIVING_BOX_ID + " BLOB)";
            db.execSQL(createFriends);

            String createLocations = "CREATE TABLE "
                    + LOCATIONS_TABLE + " ("
                    + LOCATIONS_COL_FRIEND_ID + " INTEGER PRIMARY KEY, "
                    + LOCATIONS_COL_LATITUDE + " REAL NOT NULL, "
                    + LOCATIONS_COL_LONGITUDE + " REAL NOT NULL, "
                    + LOCATIONS_COL_TIME + " INTEGER NOT NULL, "
                    + LOCATIONS_COL_ACCURACY + " REAL, "
                    + LOCATIONS_COL_SPEED + " REAL, "
                    + LOCATIONS_COL_BEARING + " REAL, "
                    + LOCATIONS_COL_MOVEMENT + " TEXT, "
                    + LOCATIONS_COL_BATTERY_LEVEL + " INTEGER, "
                    + LOCATIONS_COL_BATTERY_CHARGING + " INTEGER)";
            db.execSQL(createLocations);

            String createUsers = "CREATE TABLE "
                    + USERS_TABLE + " ("
                    + USERS_COL_ID + " INTEGER PRIMARY KEY, "
                    + USERS_COL_USER_ID + " BLOB UNIQUE NOT NULL, "
                    + USERS_COL_USERNAME + " TEXT NOT NULL, "
                    + USERS_COL_PUBLIC_KEY + " BLOB NOT NULL)";
            db.execSQL(createUsers);

            String createLimitedShares = "CREATE TABLE "
                    + LIMITED_SHARES_TABLE + " ("
                    + LIMITED_SHARES_COL_ID + " INTEGER PRIMARY KEY, "
                    + LIMITED_SHARES_COL_PUBLIC_KEY + " BLOB NOT NULL, "
                    + LIMITED_SHARES_COL_SENDING_BOX_ID + " BLOB NOT NULL)";
            db.execSQL(createLimitedShares);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            L.i("onUpgrade - old: " + oldVersion + ", new: " + newVersion);
        }
    }

    //region Listener management
    public interface Listener {
        @UiThread default void onFriendLocationUpdated(FriendLocation loc) {}

        @UiThread
        default void onFriendRemoved(long friendId) {}

        @WorkerThread default void onLocationSharingGranted(long userId) {}

        @WorkerThread default void onLocationSharingRevoked(long userId) {}

        @WorkerThread default void onStartedSharingWithUser(long userId) {}

        @UiThread default void onStoppedSharingWithUser(long userId) {}
    }

    @AnyThread
    public void addListener(@NonNull Listener listener) {
        WeakReference<Listener> ref = new WeakReference<>(listener);
        listeners.add(ref);
    }

    @AnyThread
    private void notifyFriendLocationUpdated(@NonNull FriendLocation location) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onFriendLocationUpdated(location);
                }
            }
        });
    }

    @AnyThread
    private void notifyStartedSharingWithUser(long userId) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onStartedSharingWithUser(userId);
                }
            }
        });
    }

    @AnyThread
    private void notifyStoppedSharingWithUser(long userId) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onStoppedSharingWithUser(userId);
                }
            }
        });
    }

    @AnyThread
    private void notifyFriendRemoved(long friendId) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onFriendRemoved(friendId);
                }
            }
        });
    }

    @AnyThread
    private void notifySharingGranted(long userId) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onLocationSharingGranted(userId);
                }
            }
        });
    }

    @AnyThread
    private void notifySharingRevoked(long userId) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l == null) {
                        continue;
                    }
                    l.onLocationSharingRevoked(userId);
                }
            }
        });
    }

    @AnyThread
    public void removeListener(@NonNull Listener listener) {
        int i=0;
        while (i<listeners.size()) {
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
}
