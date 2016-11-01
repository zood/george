package io.pijun.george;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;

import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;

@SuppressWarnings("WeakerAccess")
public class DB {

    private static volatile DB sDb;

    static class DBException extends Exception {
        DBException(String msg) {
            super(msg);
        }
    }

    private DBHelper mDbHelper;

    private DB(Context context) {
        mDbHelper = new DBHelper(context);
    }

    public static DB get(Context context) {
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
    public long addFriend(@NonNull String username,
                          @NonNull @Size(Constants.USER_ID_LENGTH) byte[] userId,
                          @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId,
                          boolean shareRequestedOfMe,
                          Long friendRequestSendDate) throws DBException {
        return mDbHelper.addFriend(username, userId, publicKey, sendingBoxId, receivingBoxId, shareRequestedOfMe, friendRequestSendDate);
    }

    @WorkerThread
    public void deleteUserData() {
        mDbHelper.deleteUserData();
    }

    @WorkerThread
    @Nullable
    public FriendRecord getFriend(@NonNull @Size(Constants.USER_ID_LENGTH) final byte[] userId) {
        return mDbHelper.getFriendMatchingBlob(userId, DBHelper.FRIENDS_COL_USER_ID);
    }

    @WorkerThread
    @Nullable
    public FriendRecord getFriendById(long friendId) {
        return mDbHelper.getFriendById(friendId);
    }

    @WorkerThread
    @Nullable
    public FriendRecord getFriendByReceivingBoxId(@NonNull @Size(Constants.DROP_BOX_ID_LENGTH) final byte[] boxId) {
        return mDbHelper.getFriendMatchingBlob(boxId, DBHelper.FRIENDS_COL_RECEIVING_BOX_ID);
    }

    @Nullable
    public FriendLocation getFriendLocation(long friendRecordId) {
        return mDbHelper.getFriendLocation(friendRecordId);
    }

    public int getFriendRequestsCount() {
        return mDbHelper.getFriendRequestsCount();
    }

    @WorkerThread
    @NonNull
    public ArrayList<FriendRecord> getFriends() {
        return mDbHelper.getFriends();
    }

    @WorkerThread
    @NonNull
    public ArrayList<FriendRecord> getFriendsToShareWith() {
        return mDbHelper.getFriendsToShareWith();
    }

    @WorkerThread
    public void setFriendLocation(long friendId, double lat, double lng, long time, Float accuracy, Float speed) throws DBException {
        L.i("setFriendLocation: {id: " + friendId + ", lat: " + lat + ", lng: " + lng);
        mDbHelper.setFriendLocation(friendId, lat, lng, time, accuracy, speed);
    }

    @WorkerThread
    public void setReceivingDropBoxId(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
        mDbHelper.setReceivingBoxId(username, boxId);
    }

    @WorkerThread
    public void setShareRequestedOfMe(@NonNull String username, boolean shareRequestedOfMe) throws DBException {
        mDbHelper.setShareRequestedOfMe(username, shareRequestedOfMe);
    }

    @WorkerThread
    public void setShareGranted(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
        mDbHelper.setShareGranted(username, boxId);
    }

    private static class DBHelper extends SQLiteOpenHelper {

        private static final String FRIENDS_TABLE = "friends";
        private static final String FRIENDS_COL_ID = "id";
        private static final String FRIENDS_COL_USERNAME = "username";
        private static final String FRIENDS_COL_USER_ID = "user_id";
        private static final String FRIENDS_COL_PUBLIC_KEY = "public_key";
        private static final String FRIENDS_COL_SENDING_BOX_ID = "sending_box_id";
        private static final String FRIENDS_COL_RECEIVING_BOX_ID = "receiving_box_id";
        private static final String FRIENDS_COL_SHARE_REQUESTED_OF_ME = "share_requested_of_me";
        private static final String FRIENDS_COL_FRIEND_REQUEST_SEND_DATE = "friend_request_send_date";
        private static final String[] FRIENDS_COLUMNS = new String[]{
                FRIENDS_COL_ID,
                FRIENDS_COL_USERNAME,
                FRIENDS_COL_USER_ID,
                FRIENDS_COL_PUBLIC_KEY,
                FRIENDS_COL_SENDING_BOX_ID,
                FRIENDS_COL_RECEIVING_BOX_ID,
                FRIENDS_COL_SHARE_REQUESTED_OF_ME,
                FRIENDS_COL_FRIEND_REQUEST_SEND_DATE
        };

        private static final String LOCATIONS_TABLE = "locations";
        private static final String LOCATIONS_COL_FRIEND_ID = "friend_id";
        private static final String LOCATIONS_COL_LATITUDE = "latitude";
        private static final String LOCATIONS_COL_LONGITUDE = "longitude";
        private static final String LOCATIONS_COL_TIME = "time";
        private static final String LOCATIONS_COL_ACCURACY = "accuracy";
        private static final String LOCATIONS_COL_SPEED = "speed";
        private static final String[] LOCATIONS_COLUMNS = new String[]{
                LOCATIONS_COL_FRIEND_ID,
                LOCATIONS_COL_LATITUDE,
                LOCATIONS_COL_LONGITUDE,
                LOCATIONS_COL_TIME,
                LOCATIONS_COL_ACCURACY,
                LOCATIONS_COL_SPEED
        };

        private DBHelper(Context context) {
            super(context, "thedata", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            L.i("DBHelper.onCreate");

            String createFriends = "CREATE TABLE "
                    + FRIENDS_TABLE + " ("
                    + FRIENDS_COL_ID + " INTEGER PRIMARY KEY, "
                    + FRIENDS_COL_USERNAME + " TEXT NOT NULL, "
                    + FRIENDS_COL_USER_ID + " BLOB UNIQUE NOT NULL, "
                    + FRIENDS_COL_PUBLIC_KEY + " BLOB NOT NULL, "
                    + FRIENDS_COL_SENDING_BOX_ID + " BLOB, "
                    + FRIENDS_COL_RECEIVING_BOX_ID + " BLOB, "
                    + FRIENDS_COL_SHARE_REQUESTED_OF_ME + " INTEGER NOT NULL, " // use as a boolean
                    + FRIENDS_COL_FRIEND_REQUEST_SEND_DATE + " INTEGER)";  // unix epoch time
            db.execSQL(createFriends);

            String createLocations = "CREATE TABLE "
                    + LOCATIONS_TABLE + " ("
                    + LOCATIONS_COL_FRIEND_ID + " INTEGER PRIMARY KEY, "
                    + LOCATIONS_COL_LATITUDE + " REAL NOT NULL, "
                    + LOCATIONS_COL_LONGITUDE + " REAL NOT NULL, "
                    + LOCATIONS_COL_TIME + " INTEGER NOT NULL, "
                    + LOCATIONS_COL_ACCURACY + " REAL, "
                    + LOCATIONS_COL_SPEED + " REAL)";
            db.execSQL(createLocations);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            L.i("onUpgrade - old: " + oldVersion + ", new: " + newVersion);
        }

        @WorkerThread
        private long addFriend(@NonNull String username,
                               @NonNull @Size(Constants.USER_ID_LENGTH) byte[] userId,
                               @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey,
                               @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                               @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId,
                               boolean shareRequestedOfMe,
                               Long friendRequestSendDate) throws DBException {
            //noinspection ConstantConditions
            if (username == null) {
                throw new IllegalArgumentException("username must not be null");
            }
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_USERNAME, username);
            cv.put(FRIENDS_COL_USER_ID, userId);
            cv.put(FRIENDS_COL_PUBLIC_KEY, publicKey);
            cv.put(FRIENDS_COL_SENDING_BOX_ID, sendingBoxId);
            cv.put(FRIENDS_COL_RECEIVING_BOX_ID, receivingBoxId);
            cv.put(FRIENDS_COL_SHARE_REQUESTED_OF_ME, shareRequestedOfMe);
            cv.put(FRIENDS_COL_FRIEND_REQUEST_SEND_DATE, friendRequestSendDate);
            long result = db.insert(FRIENDS_TABLE, null, cv);
            if (result == 0) {
                throw new DBException("Error creating friend " + username);
            }
            return result;
        }

        @WorkerThread
        private void deleteUserData() {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(FRIENDS_TABLE, null, null);
        }

        @WorkerThread
        @Nullable
        private FriendRecord getFriendById(long friendId) {
            FriendRecord friend = null;
            SQLiteDatabase db = getReadableDatabase();
            String selection = FRIENDS_COL_ID + "=?";
            String[] selectionArgs = new String[]{
                    String.valueOf(friendId)
            };
            try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, selectionArgs, null, null, null)) {
                if (c.moveToNext()) {
                    friend = readFromCursor(c);
                }
            }
            return friend;
        }

        @WorkerThread
        @Nullable
        private FriendLocation getFriendLocation(long friendRecordId) {
            FriendLocation fl = null;
            SQLiteDatabase db = getReadableDatabase();
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
                    fl = new FriendLocation(friendRecordId, lat, lng, time, acc, speed);
                }
            }

            return fl;
        }

        @WorkerThread
        @Nullable
        private FriendRecord getFriendMatchingBlob(@NonNull final byte[] blob, @NonNull String matchingColumn) {
            FriendRecord fr = null;
            SQLiteDatabase db = getReadableDatabase();
            StringBuilder sql = new StringBuilder("SELECT ");
            String delim = "";
            for (String col : FRIENDS_COLUMNS) {
                sql.append(delim).append(col);
                delim = ",";
            }
            sql.append(" FROM ").append(FRIENDS_TABLE).append(" WHERE ").append(matchingColumn).append("=?");
//            String sql = "SELECT " + FRIENDS_COL_ID + ", "
//                    + FRIENDS_COL_USERNAME + ", "
//                    + FRIENDS_COL_USER_ID + ", "
//                    + FRIENDS_COL_PUBLIC_KEY + ", "
//                    + FRIENDS_COL_SENDING_BOX_ID + ", "
//                    + FRIENDS_COL_RECEIVING_BOX_ID + ", "
//                    + FRIENDS_COL_SHARE_REQUESTED_OF_ME + ", "
//                    + FRIENDS_COL_SHARE_REQUEST_NOTE
//                    + " FROM " + FRIENDS_TABLE
//                    + " WHERE " + matchingColumn + "=?";
            SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
                @Override
                public Cursor newCursor(SQLiteDatabase sqLiteDatabase, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
                    query.bindBlob(1, blob);
                    return new SQLiteCursor(driver, editTable, query);
                }
            };
            try (Cursor c = db.rawQueryWithFactory(factory, sql.toString(), null, FRIENDS_TABLE)) {
                if (c.moveToNext()) {
                    fr = readFromCursor(c);
                }
            }

            return fr;
        }

        @WorkerThread
        private int getFriendRequestsCount() {
            int count = 0;
            SQLiteDatabase db = getReadableDatabase();
            String sql = "SELECT COUNT(*) FROM " + FRIENDS_TABLE + " WHERE " + FRIENDS_COL_SHARE_REQUESTED_OF_ME + "=1 AND " + FRIENDS_COL_SENDING_BOX_ID + " ISNULL";
            try (Cursor cursor = db.rawQuery(sql, null)) {
                if (cursor.moveToNext()) {
                    count = cursor.getInt(0);
                }
            }

            return count;
        }

        @WorkerThread
        @NonNull
        private ArrayList<FriendRecord> getFriends() {
            ArrayList<FriendRecord> records = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    FriendRecord fr = readFromCursor(cursor);
                    records.add(fr);
                }
            }

            return records;
        }

        @WorkerThread
        @NonNull
        private ArrayList<FriendRecord> getFriendsToShareWith() {
            ArrayList<FriendRecord> records = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, FRIENDS_COL_SENDING_BOX_ID + " IS NOT NULL", null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    FriendRecord fr = readFromCursor(cursor);
                    records.add(fr);
                }
            }

            return records;
        }

        @WorkerThread
        private void setFriendLocation(long friendId, double lat, double lng, long time, Float accuracy, Float speed) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
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
            long result = db.replace(LOCATIONS_TABLE, null, cv);
            if (result == -1) {
                throw new DBException("Error occurred while setting friend location");
            }
        }

        @WorkerThread
        private void setReceivingBoxId(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_RECEIVING_BOX_ID, boxId);
            long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }

        @WorkerThread
        private void setShareRequestedOfMe(@NonNull String username, boolean shareRequested) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SHARE_REQUESTED_OF_ME, shareRequested);
            long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }

        @WorkerThread
        private void setShareGranted(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SENDING_BOX_ID, boxId);
            cv.put(FRIENDS_COL_SHARE_REQUESTED_OF_ME, false);
            long result = db.update(FRIENDS_TABLE, cv, FRIENDS_COL_USERNAME + "=?", new String[]{username});
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }

        @WorkerThread
        @NonNull
        private FriendRecord readFromCursor(Cursor c) {
            FriendRecord f = new FriendRecord();
            f.id = c.getLong(c.getColumnIndexOrThrow(FRIENDS_COL_ID));
            f.userId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_USER_ID));
            f.username = c.getString(c.getColumnIndexOrThrow(FRIENDS_COL_USERNAME));
            f.publicKey = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_PUBLIC_KEY));
            f.sendingBoxId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_SENDING_BOX_ID));
            f.receivingBoxId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_RECEIVING_BOX_ID));
            f.shareRequestedOfMe = c.getInt(c.getColumnIndexOrThrow(FRIENDS_COL_SHARE_REQUESTED_OF_ME)) != 0;
            int frsdColIdx = c.getColumnIndexOrThrow(FRIENDS_COL_FRIEND_REQUEST_SEND_DATE);
            if (!c.isNull(frsdColIdx)) {
                f.friendRequestSendDate = c.getLong(frsdColIdx);
            }

            return f;
        }
    }
}
