package io.pijun.george;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;

import io.pijun.george.models.FriendRecord;

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
    public long addFriendWithSharingRequest(@NonNull String username, @NonNull @Size(Constants.USER_ID_LENGTH) byte[] userId, @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey) throws DBException {
        return mDbHelper.addFriendWithSharingRequest(username, userId, publicKey);
    }

    @WorkerThread
    public void deleteUserData() {
        mDbHelper.deleteUserData();
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
    public void setSendingDropBoxId(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) {
        mDbHelper.setSendingDropBoxId(username, boxId);
    }

    private static class DBHelper extends SQLiteOpenHelper {

        private static final String FRIENDS_TABLE = "friends";
        private static final String FRIENDS_COL_ID = "id";
        private static final String FRIENDS_COL_USERNAME = "username";
        private static final String FRIENDS_COL_USER_ID = "user_id";
        private static final String FRIENDS_COL_PUBLIC_KEY = "public_key";
        private static final String FRIENDS_COL_SENDING_BOX_ID = "sending_box_id";
        private static final String FRIENDS_COL_RECEIVING_BOX_ID = "receiving_box_id";
        private static final String FRIENDS_COL_SHARE_REQUEST_NOTE = "share_request_note";

        public DBHelper(Context context) {
            super(context, "thedata", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            L.i("DBHelper.onCreate");

            String createFriends = "CREATE TABLE "
                    + FRIENDS_TABLE + " ("
                    + FRIENDS_COL_ID + " INTEGER PRIMARY KEY, "
                    + FRIENDS_COL_USERNAME + " TEXT NOT NULL, "
                    + FRIENDS_COL_USER_ID + " BLOB NOT NULL, "
                    + FRIENDS_COL_PUBLIC_KEY + " BLOB NOT NULL, "
                    + FRIENDS_COL_SENDING_BOX_ID + " BLOB, "
                    + FRIENDS_COL_RECEIVING_BOX_ID + " BLOB,"
                    + FRIENDS_COL_SHARE_REQUEST_NOTE + " TEXT)";
            db.execSQL(createFriends);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            L.i("onUpgrade - old: " + oldVersion + ", new: " + newVersion);
        }

        @WorkerThread
        private long addFriendWithSharingRequest(@NonNull String username, @NonNull @Size(Constants.USER_ID_LENGTH) byte[] userId, @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey) throws DBException {
            //noinspection ConstantConditions
            if (username == null) {
                throw new IllegalArgumentException("username must not be null");
            }
            L.i("inserting username " + username);
            try (SQLiteDatabase db = getWritableDatabase()) {
                ContentValues cv = new ContentValues();
                cv.put(FRIENDS_COL_USERNAME, username);
                cv.put(FRIENDS_COL_USER_ID, userId);
                cv.put(FRIENDS_COL_PUBLIC_KEY, publicKey);
                long result = db.insert(FRIENDS_TABLE, null, cv);
                if (result == 0) {
                    throw new DBException("Error creating friend " + username + " with sharing request");
                }
                return result;
            } catch (Throwable t) {
                L.w("exception in the catcher", t);
            }

            return 0;
        }

        @WorkerThread
        private void deleteUserData() {
            try (SQLiteDatabase db = getWritableDatabase()) {
                db.delete(FRIENDS_TABLE, null, null);
            }
        }

        /*
        @WorkerThread
        private void setSendingDropBoxId(@NonNull String username, @NonNull @Size(512) byte[] boxId) {
            try (SQLiteDatabase db = getWritableDatabase()) {
                ContentValues cv = new ContentValues();
                cv.put(FRIENDS_COL_SENDING_BOX_ID, boxId);
                long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
                if (result != 1) {
                    L.w("DBHelper.setSendingDropBoxId - Num affected rows was " + result + " for username '" + username + "'");
                }
            }
        }
        */

        @WorkerThread
        @NonNull
        private ArrayList<FriendRecord> getFriends() {
            ArrayList<FriendRecord> records = new ArrayList<>();
            try (SQLiteDatabase db = getReadableDatabase()) {
                String[] cols = new String[]{
                        FRIENDS_COL_ID,
                        FRIENDS_COL_USERNAME,
                        FRIENDS_COL_USER_ID,
                        FRIENDS_COL_PUBLIC_KEY,
                        FRIENDS_COL_SENDING_BOX_ID,
                        FRIENDS_COL_RECEIVING_BOX_ID,
                        FRIENDS_COL_SHARE_REQUEST_NOTE};
                Cursor cursor = db.query(FRIENDS_TABLE, cols, null, null, null, null, null, null);
                int idIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_ID);
                int usernameIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_USERNAME);
                int userIdIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_USER_ID);
                int publicKeyIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_PUBLIC_KEY);
                int sendingBoxIdIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_SENDING_BOX_ID);
                int receivingBoxIdIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_RECEIVING_BOX_ID);
                int shareRequestNoteIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_SHARE_REQUEST_NOTE);
                while (cursor.moveToNext()) {
                    FriendRecord fr = new FriendRecord();
                    fr.id = cursor.getInt(idIdx);
                    fr.username = cursor.getString(usernameIdx);
                    fr.userId = cursor.getBlob(userIdIdx);
                    fr.publicKey = cursor.getBlob(publicKeyIdx);
                    fr.sendingBoxId = cursor.getBlob(sendingBoxIdIdx);
                    fr.receivingBoxId = cursor.getBlob(receivingBoxIdIdx);
                    fr.shareRequestNote = cursor.getString(shareRequestNoteIdx);
                    records.add(fr);
                }
            }

            return records;
        }

        @WorkerThread
        @NonNull
        private ArrayList<FriendRecord> getFriendsToShareWith() {
            ArrayList<FriendRecord> records = new ArrayList<>();
            try (SQLiteDatabase db = getReadableDatabase()) {
                String[] cols = new String[]{
                        FRIENDS_COL_ID,
                        FRIENDS_COL_USERNAME,
                        FRIENDS_COL_USER_ID,
                        FRIENDS_COL_PUBLIC_KEY,
                        FRIENDS_COL_SENDING_BOX_ID,
                        FRIENDS_COL_RECEIVING_BOX_ID,
                        FRIENDS_COL_SHARE_REQUEST_NOTE};
                Cursor cursor = db.query(FRIENDS_TABLE, cols, FRIENDS_COL_SENDING_BOX_ID + " IS NOT NULL", null, null, null, null, null);
                int idIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_ID);
                int usernameIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_USERNAME);
                int userIdIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_USER_ID);
                int publicKeyIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_PUBLIC_KEY);
                int sendingBoxIdIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_SENDING_BOX_ID);
                int receivingBoxIdIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_RECEIVING_BOX_ID);
                int shareRequestNoteIdx = cursor.getColumnIndexOrThrow(FRIENDS_COL_SHARE_REQUEST_NOTE);
                while (cursor.moveToNext()) {
                    FriendRecord fr = new FriendRecord();
                    fr.id = cursor.getInt(idIdx);
                    fr.username = cursor.getString(usernameIdx);
                    fr.userId = cursor.getBlob(userIdIdx);
                    fr.publicKey = cursor.getBlob(publicKeyIdx);
                    fr.sendingBoxId = cursor.getBlob(sendingBoxIdIdx);
                    fr.receivingBoxId = cursor.getBlob(receivingBoxIdIdx);
                    fr.shareRequestNote = cursor.getString(shareRequestNoteIdx);
                    records.add(fr);
                }
            }

            return records;
        }

        @WorkerThread
        private void setSendingDropBoxId(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) {
            try (SQLiteDatabase db = getWritableDatabase()) {
                ContentValues cv = new ContentValues();
                cv.put(FRIENDS_COL_SENDING_BOX_ID, boxId);
                long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
                if (result != 1) {
                    L.w("DBHelper.setSendingDropBoxId - Num affected rows was " + result + " for username '" + username + "'");
                }
            }
        }
    }
}
