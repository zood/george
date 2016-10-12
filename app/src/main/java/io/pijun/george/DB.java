package io.pijun.george;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

import io.pijun.george.models.GrantedShare;
import io.pijun.george.models.ShareRequest;

public class DB extends SQLiteOpenHelper {

    private static final String GRANTED_SHARES_TABLE = "granted_shares";
    private static final String GRANTED_SHARES_COL_ID = "id";
    private static final String GRANTED_SHARES_COL_USERNAME = "username";
    private static final String GRANTED_SHARES_COL_USERID = "user_id";
    private static final String GRANTED_SHARES_COL_BOXID = "box_id";

    private static final String SHARE_REQUESTS_TABLE = "share_requests";
    private static final String SHARE_REQUESTS_COL_ID = "id";
    private static final String SHARE_REQUESTS_COL_USERNAME = "username";
    private static final String SHARE_REQUESTS_COL_USERID = "user_id";
    private static final String SHARE_REQUESTS_COL_MESSAGE = "message";

    public DB(Context context) {
        super(context, "thedata", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        L.i("DB.onCreate");
        String createGrantedShares = "CREATE TABLE "
                + GRANTED_SHARES_TABLE + " ("
                + GRANTED_SHARES_COL_ID + " INTEGER PRIMARY KEY, "
                + GRANTED_SHARES_COL_USERNAME + " TEXT NOT NULL, "
                + GRANTED_SHARES_COL_USERID + " BLOB NOT NULL, "
                + GRANTED_SHARES_COL_BOXID + " BLOB NOT NULL)";
        db.execSQL(createGrantedShares);

        String createShareRequests = "CREATE TABLE "
                + SHARE_REQUESTS_TABLE + " ("
                + SHARE_REQUESTS_COL_ID + " INTEGER PRIMARY KEY, "
                + SHARE_REQUESTS_COL_USERNAME + " TEXT NOT NULL, "
                + SHARE_REQUESTS_COL_USERID + " BLOB NOT NULL, "
                + SHARE_REQUESTS_COL_MESSAGE + " TEXT NOT NULL)";
        db.execSQL(createShareRequests);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        L.i("onUpgrade - old: " + oldVersion + ", new: " + newVersion);
    }

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
}
