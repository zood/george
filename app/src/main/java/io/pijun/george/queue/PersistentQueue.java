package io.pijun.george.queue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.crashlytics.android.Crashlytics;

import java.util.concurrent.Semaphore;

import io.pijun.george.L;

public class PersistentQueue<E> {

    private static final String TASKS_TABLE = "tasks";
    private static final String TASKS_COL_ID = "id";
    private static final String TASKS_COL_DATA = "data";

    private final QueueHelper mHelper;
    private final Converter<E> mConverter;
    private final Semaphore mSemaphore;

    public PersistentQueue(@NonNull Context context, @NonNull String queueName, @NonNull Converter<E> converter) {
        mConverter = converter;
        mHelper = new QueueHelper(context, queueName);
        mSemaphore = new Semaphore(size(), true);
    }

    @WorkerThread
    @CheckResult
    @NonNull
    public E blockingPeek() throws InterruptedException {
        mSemaphore.acquire();
        try {
            E element = getHead(false);
            if (element == null) {
                throw new RuntimeException("Mismatch between semaphore permits and actual queue count");
            }
            return element;
        } finally {
            mSemaphore.release();
        }
    }

    @WorkerThread
    public void clear() {
        mSemaphore.drainPermits();
        SQLiteDatabase db = mHelper.getWritableDatabase();
        db.delete(TASKS_TABLE, null, null);
    }

    private void deleteRow(int itemId) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        int num = db.delete(TASKS_TABLE, TASKS_COL_ID + "=?", new String[]{""+itemId});
        if (num != 1) {
            throw new RuntimeException("Deleting the queue item affected " + num + " rows");
        }
    }

    @WorkerThread
    @Nullable
    private E getHead(boolean thenDelete) {
        E element = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        int itemId = 0;
        try (Cursor c = db.query(TASKS_TABLE, new String[]{TASKS_COL_ID, TASKS_COL_DATA}, null, null, null, null, TASKS_COL_ID + " ASC", "1")) {
            if (c.moveToNext()) {
                itemId = c.getInt(c.getColumnIndexOrThrow(TASKS_COL_ID));
                byte[] data = c.getBlob(c.getColumnIndexOrThrow(TASKS_COL_DATA));
                element = mConverter.deserialize(data);
            }
        }

        if (thenDelete && itemId != 0) {
            deleteRow(itemId);
        }

        return element;
    }

    @WorkerThread
    public void offer(E e) {
        byte[] bytes = mConverter.serialize(e);
        ContentValues cv = new ContentValues();
        cv.put(TASKS_COL_DATA, bytes);
        try {
            mHelper.getWritableDatabase().insertOrThrow(TASKS_TABLE, null, cv);
        } catch (SQLException ex) {
            L.w("Error offering", ex);
            Crashlytics.logException(ex);
            return;
        }
        mSemaphore.release();
    }

    @Nullable
    @WorkerThread
    @CheckResult
    public E peek() {
        if (mSemaphore.tryAcquire()) {
            try {
                E element = getHead(false);
                if (element == null) {
                    throw new RuntimeException("Mismatch between semaphore permits and actual queue count");
                }
                return element;
            } finally {
                mSemaphore.release();
            }
        }

        return null;
    }

    @WorkerThread
    @Nullable
    public E poll() {
        if (mSemaphore.tryAcquire()) {
            E element = getHead(true);
            if (element == null) {
                throw new RuntimeException("Mismatch between semaphore permits and actual queue count");
            }
            return element;
        }

        return null;
    }

    @CheckResult
    @WorkerThread
    public int size() {
        int cnt = -1;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TASKS_TABLE, null)) {
            if (c.moveToNext()) {
                cnt = c.getInt(0);
            }
        }

        if (cnt == -1) {
            throw new RuntimeException("Error counting queue elements");
        }

        return cnt;
    }

    @WorkerThread
    @NonNull
    public E take() throws InterruptedException {
        mSemaphore.acquire();
        E element = getHead(true);
        if (element == null) {
            throw new RuntimeException("Mismatch between semaphore permits and actual queue count");
        }
        return element;
    }

    private static class QueueHelper extends SQLiteOpenHelper {

        private QueueHelper(Context context, String queueName) {
            super(context, "queue_" + queueName, null, 1);
        }

        public void onCreate(SQLiteDatabase db) {
            String createTasks = "CREATE TABLE "
                    + TASKS_TABLE + " ("
                    + TASKS_COL_ID + " INTEGER PRIMARY KEY, "
                    + TASKS_COL_DATA + " BLOB)";
            db.execSQL(createTasks);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            L.i("QueueHelper.onUpgrade {oldVersion: " + oldVersion + ", newVersion: " + newVersion + "}");
        }
    }

    public interface Converter<E> {
        E deserialize(byte[] bytes);
        byte[] serialize(E t);
    }

}
