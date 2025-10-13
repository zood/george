package io.pijun.george.queue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.pijun.george.L;

public class PersistentQueue<E> {

    private static final String TASKS_TABLE = "tasks";
    private static final String TASKS_COL_ID = "id";
    private static final String TASKS_COL_DATA = "data";

    private final QueueHelper mHelper;
    private final Converter<E> mConverter;
    private final Semaphore mSemaphore;

    public PersistentQueue(@NonNull Context context, @Nullable String queueName, @NonNull Converter<E> converter) {
        mConverter = converter;
        String name = null;
        if (queueName != null) {
            name = "queue_"+queueName;
        }
        mHelper = new QueueHelper(context, name);

        // This has to be initialized after mHelper, because it relies on size(), which needs mHelper
        mSemaphore = new Semaphore(size(), true);
    }

    @WorkerThread
    @CheckResult
    @NonNull
    public E blockingPeek() {
        try {
            mSemaphore.acquire();
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted while acquiring semaphore", ie);
        }
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
        while (true) {
            if (!mSemaphore.tryAcquire()) {
                return;
            }
            getHead(true);
        }
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
        E element;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        int itemId = 0;
        int colSize = 0;
        // read back the row, and data length
        try (Cursor c = db.query(TASKS_TABLE, new String[]{TASKS_COL_ID, "length("+TASKS_COL_DATA+")"}, null, null, null, null, TASKS_COL_ID + " ASC", "1")) {
            if (c.moveToNext()) {
                itemId = c.getInt(0);
                colSize = c.getInt(1);
            }
        }

        // if there was no row, return null
        if (itemId == 0) {
            return null;
        }

        // otherwise, read in the data 768 KB at a time
        int offset = 1;
        int bytesRead = 0;
        int chunkSize = 768 * 1024; // 768 KB
        ByteBuffer buffer = ByteBuffer.allocate(colSize);
        String template = "SELECT substr(%s, %d, %d) FROM %s WHERE id=%d";
        while (bytesRead < colSize) {
            if (bytesRead + chunkSize > colSize) {
                chunkSize = colSize - bytesRead;
            }
            String query = String.format(Locale.US, template, TASKS_COL_DATA, offset, chunkSize, TASKS_TABLE, itemId);
            try (Cursor c = db.rawQuery(query, null)) {
                if (!c.moveToNext()) {
                    throw new RuntimeException("No row found while reading data bytes. Something wrong with your math?");
                }
                byte[] data = c.getBlob(0);
                buffer.put(data);
            }
            bytesRead += chunkSize;
            offset += chunkSize;
        }

        element = mConverter.deserialize(buffer.array());
        if (thenDelete) {
            deleteRow(itemId);
        }

        return element;
    }

    @WorkerThread
    public void offer(@NonNull E e) {
        byte[] bytes = mConverter.serialize(e);
        ContentValues cv = new ContentValues();
        cv.put(TASKS_COL_DATA, bytes);
        try {
            mHelper.getWritableDatabase().insertOrThrow(TASKS_TABLE, null, cv);
        } catch (SQLException ex) {
            L.w("PersistentQueue.offer", ex);
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
    public E take() {
        try {
            mSemaphore.acquire();
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted while acquiring semaphore", ie);
        }
        E element = getHead(true);
        if (element == null) {
            throw new RuntimeException("Mismatch between semaphore permits and actual queue count");
        }
        return element;
    }

    private static class QueueHelper extends SQLiteOpenHelper {

        private QueueHelper(@NonNull Context context, @Nullable String queueName) {
            super(context, queueName, null, 1);
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
        E deserialize(@NonNull byte[] bytes);
        @NonNull byte[] serialize(@NonNull E item);
    }

}
