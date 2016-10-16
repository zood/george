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

public class DB extends SQLiteOpenHelper {

//    private static final String GRANTED_SHARES_TABLE = "granted_shares";
//    private static final String GRANTED_SHARES_COL_ID = "id";
//    private static final String GRANTED_SHARES_COL_USERNAME = "username";
//    private static final String GRANTED_SHARES_COL_USERID = "user_id";
//    private static final String GRANTED_SHARES_COL_BOXID = "box_id";
//
//    private static final String SHARE_REQUESTS_TABLE = "share_requests";
//    private static final String SHARE_REQUESTS_COL_ID = "id";
//    private static final String SHARE_REQUESTS_COL_USERNAME = "username";
//    private static final String SHARE_REQUESTS_COL_USERID = "user_id";
//    private static final String SHARE_REQUESTS_COL_MESSAGE = "message";

    private static final String FRIENDS_TABLE = "friends";
    private static final String FRIENDS_COL_ID = "id";
    private static final String FRIENDS_COL_USERNAME = "username";
    private static final String FRIENDS_COL_USER_ID = "user_id";
    private static final String FRIENDS_COL_PUBLIC_KEY = "public_key";
    private static final String FRIENDS_COL_SENDING_BOX_ID = "sending_box_id";
    private static final String FRIENDS_COL_RECEIVING_BOX_ID = "receiving_box_id";
    private static final String FRIENDS_COL_SHARE_REQUEST_NOTE = "share_request_note";

    static class DBException extends Exception {
        DBException(String msg) {
            super(msg);
        }
    }

    public DB(Context context) {
        super(context, "thedata", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        L.i("DB.onCreate");
//        String createGrantedShares = "CREATE TABLE "
//                + GRANTED_SHARES_TABLE + " ("
//                + GRANTED_SHARES_COL_ID + " INTEGER PRIMARY KEY, "
//                + GRANTED_SHARES_COL_USERNAME + " TEXT NOT NULL, "
//                + GRANTED_SHARES_COL_USERID + " BLOB NOT NULL, "
//                + GRANTED_SHARES_COL_BOXID + " BLOB NOT NULL)";
//        db.execSQL(createGrantedShares);
//
//        String createShareRequests = "CREATE TABLE "
//                + SHARE_REQUESTS_TABLE + " ("
//                + SHARE_REQUESTS_COL_ID + " INTEGER PRIMARY KEY, "
//                + SHARE_REQUESTS_COL_USERNAME + " TEXT NOT NULL, "
//                + SHARE_REQUESTS_COL_USERID + " BLOB NOT NULL, "
//                + SHARE_REQUESTS_COL_MESSAGE + " TEXT NOT NULL)";
//        db.execSQL(createShareRequests);

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
    public long addFriendWithSharingRequest(@NonNull String username, @NonNull @Size(32) byte[] userId, @NonNull @Size(32) byte[] publicKey) throws DBException {
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
    public void setSendingDropBoxId(@NonNull String username, @NonNull @Size(512) byte[] boxId) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SENDING_BOX_ID, boxId);
            long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
            if (result != 1) {
                L.w("DB.setSendingDropBoxId - Num affected rows was " + result + " for username '" + username + "'");
            }
        }
    }

    @WorkerThread
    @NonNull
    public ArrayList<FriendRecord> getFriends() {
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

    /*
    public long addGrantedShare(String username, byte[] userId, byte[] boxId) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            ContentValues cv = new ContentValues();
            cv.put(GRANTED_SHARES_COL_USERNAME, username);
            cv.put(GRANTED_SHARES_COL_USERID, userId);
            cv.put(GRANTED_SHARES_COL_BOXID, boxId);
            long result = db.insert(GRANTED_SHARES_TABLE, null, cv);
            db.close();

            return result;
        }
    }

    public ArrayList<GrantedShare> getGrantedShares() {
        ArrayList<GrantedShare> shares = new ArrayList<>();
        try (SQLiteDatabase db = getReadableDatabase()) {
            Cursor cursor = db.query(GRANTED_SHARES_TABLE,
                    new String[]{GRANTED_SHARES_COL_USERNAME, GRANTED_SHARES_COL_USERID, GRANTED_SHARES_COL_BOXID},
                    null, null, null, null, null, null);
            int usernameIdx = cursor.getColumnIndexOrThrow(GRANTED_SHARES_COL_USERNAME);
            int userIdIdx = cursor.getColumnIndexOrThrow(GRANTED_SHARES_COL_USERID);
            int boxIdIdx = cursor.getColumnIndexOrThrow(GRANTED_SHARES_COL_BOXID);
            while (cursor.moveToNext()) {
                GrantedShare share = new GrantedShare();
                share.username = cursor.getString(usernameIdx);
                share.userId = cursor.getBlob(userIdIdx);
                share.boxId = cursor.getBlob(boxIdIdx);
                shares.add(share);
            }
            cursor.close();
            db.close();
        }

        return shares;
    }

    public long addShareRequest(String username, byte[] userId, String msg) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            ContentValues cv = new ContentValues();
            cv.put(SHARE_REQUESTS_COL_USERNAME, username);
            cv.put(SHARE_REQUESTS_COL_USERID, userId);
            cv.put(SHARE_REQUESTS_COL_MESSAGE, msg);
            long result = db.insert(SHARE_REQUESTS_TABLE, null, cv);
            db.close();

            return result;
        }
    }

    public ArrayList<ShareRequest> getShareRequests() {
        ArrayList<ShareRequest> requests = new ArrayList<>();
        try (SQLiteDatabase db = getReadableDatabase()) {
            Cursor cursor = db.query(SHARE_REQUESTS_TABLE,
                    new String[]{SHARE_REQUESTS_COL_ID, SHARE_REQUESTS_COL_USERNAME, SHARE_REQUESTS_COL_USERID, SHARE_REQUESTS_COL_MESSAGE},
                    null, null, null, null, null, null);
            int idIdx = cursor.getColumnIndexOrThrow(SHARE_REQUESTS_COL_ID);
            int usernameIdx = cursor.getColumnIndexOrThrow(SHARE_REQUESTS_COL_USERNAME);
            int userIdIdx = cursor.getColumnIndexOrThrow(SHARE_REQUESTS_COL_USERID);
            int messageIdx = cursor.getColumnIndexOrThrow(SHARE_REQUESTS_COL_MESSAGE);
            while (cursor.moveToNext()) {
                ShareRequest sr = new ShareRequest();
                sr.id = cursor.getLong(idIdx);
                sr.username = cursor.getString(usernameIdx);
                sr.userId = cursor.getBlob(userIdIdx);
                sr.message = cursor.getString(messageIdx);
                requests.add(sr);
            }
            cursor.close();
            db.close();
        }

        return requests;
    }
    */
}
