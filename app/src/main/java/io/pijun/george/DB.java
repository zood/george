package io.pijun.george;

import android.app.job.JobScheduler;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.WorkerThread;
import android.support.v4.util.LongSparseArray;

import com.google.firebase.crash.FirebaseCrash;

import java.util.ArrayList;
import java.util.List;

import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.LimitedShare;
import io.pijun.george.models.MovementType;
import io.pijun.george.models.Snapshot;
import io.pijun.george.models.UserRecord;
import io.pijun.george.service.BackupDatabaseJob;

@SuppressWarnings("WeakerAccess")
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
    private static final String LOCATIONS_COL_MOVEMENTS = "movements";
    private static final String[] LOCATIONS_COLUMNS = new String[]{
            LOCATIONS_COL_FRIEND_ID,
            LOCATIONS_COL_LATITUDE,
            LOCATIONS_COL_LONGITUDE,
            LOCATIONS_COL_TIME,
            LOCATIONS_COL_ACCURACY,
            LOCATIONS_COL_SPEED,
            LOCATIONS_COL_BEARING,
            LOCATIONS_COL_MOVEMENTS,
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

    private static volatile DB sDb;

    public static class DBException extends Exception {
        DBException(String msg) {
            super(msg);
        }

        DBException(String msg, Throwable t) {
            super(msg, t);
        }
    }

    private final DBHelper mDbHelper;
    private final Context mContext;

    @WorkerThread
    private DB(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mDbHelper = new DBHelper(mContext);
    }

    @NonNull
    @WorkerThread
    public static DB get(@NonNull Context context) {
        if (sDb == null) {
            synchronized (DB.class) {
                if (sDb == null) {
                    sDb = new DB(context);
                }
            }
        }

        return sDb;
    }

    @WorkerThread
    public long addFriend(long userId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId) throws DBException {
        return addFriend(userId, sendingBoxId, receivingBoxId, true);
    }

    @WorkerThread
    private long addFriend(long userId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId,
                          boolean triggerBackup) throws DBException {
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

        if (triggerBackup) {
            scheduleBackup();
        }

        return result;
    }

    @WorkerThread
    public long addLimitedShare(@NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId) throws DBException {
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
            throw new DBException("Error adding limited share {pubKey:"+Hex.toHexString(publicKey) + ", sendingBoxId:"+Hex.toHexString(sendingBoxId) + "}", ex);
        }

        // NOTE: We don't bother scheduling a backup here because this data is (purposely) not
        // included in a snapshot.
        return result;
    }

    @WorkerThread
    @NonNull
    public UserRecord addUser(@NonNull @Size(Constants.USER_ID_LENGTH) final byte[] userId,
                              @NonNull String username,
                              @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey) throws DBException {
        return addUser(userId, username, publicKey, true);
    }

    @WorkerThread
    @NonNull
    public UserRecord addUser(@NonNull @Size(Constants.USER_ID_LENGTH) final byte[] userId,
                              @NonNull String username,
                              @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey,
                              boolean triggerBackup) throws DBException {
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

        if (triggerBackup) {
            scheduleBackup();
        }

        return user;
    }

    @WorkerThread
    public void deleteLimitedShares() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.delete(LIMITED_SHARES_TABLE, null, null);
    }

    @WorkerThread
    public void deleteUserData() {
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
                friend.user = getUserById(friend.userId);
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
                friend.user = getUserById(userId);
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
                List<MovementType> movements;
                int movementsColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_MOVEMENTS);
                movements = MovementType.deserialize(c.getString(movementsColIdx));
                fl = new FriendLocation(friendRecordId, lat, lng, time, acc, speed, bearing, movements);
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
                fr.user = getUserById(fr.userId);
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
                fr.user = getUserById(fr.userId);
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
                fr.user = getUserById(fr.userId);
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
                    friendRecord.user = getUserById(friendRecord.userId);
                    if (friendRecord.user == null) {
                        FirebaseCrash.report(new DBException("unable to find a user record for a friend"));
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
    public UserRecord getUserById(long id) {
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
    public void restoreDatabase(@NonNull Snapshot snapshot) throws DBException {
        L.i("snapshot: " + new String(snapshot.toJson()));
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (snapshot.schemaVersion > db.getVersion()) {
            throw new DBException("Snapshot is from a newer database format");
        }

        try {
            db.beginTransaction();
            deleteUserData();

            for (Snapshot.User u : snapshot.users) {
                addUser(u.userId, u.username, u.publicKey, false);
            }
            for (Snapshot.Friend f : snapshot.friends) {
                UserRecord user = getUser(f.userId);
                if (user == null) {
                    throw new DBException("Found friend without corresponding user");
                }
                addFriend(user.id, f.sendingBoxId, f.receivingBoxId, false);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

    }

    @WorkerThread
    private void scheduleBackup() {
        JobScheduler scheduler = (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(BackupDatabaseJob.getJobInfo(mContext));
    }

    @WorkerThread
    public void setFriendLocation(long friendId, double lat, double lng, long time, Float accuracy, Float speed, Float bearing) throws DBException {
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
        long result = db.replace(LOCATIONS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error occurred while setting friend location");
        }
    }

    @WorkerThread
    public void sharingGrantedBy(@NonNull UserRecord user, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
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

        scheduleBackup();
    }

    @WorkerThread
    public void sharingRevokedBy(@NonNull UserRecord user) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FRIENDS_COL_RECEIVING_BOX_ID, (byte[]) null);
        String whereClause = FRIENDS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(user.id)};
        db.update(FRIENDS_TABLE, cv, whereClause, args);
        scheduleBackup();
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
        scheduleBackup();
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
        scheduleBackup();
    }

    private static class DBHelper extends SQLiteOpenHelper {

        private DBHelper(Context context) {
            super(context, "thedata2", null, 1);
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
                    + LOCATIONS_COL_MOVEMENTS + " TEXT)";
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
}
