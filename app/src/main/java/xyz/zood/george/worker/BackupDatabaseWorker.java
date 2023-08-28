package xyz.zood.george.worker;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import io.pijun.george.AuthenticationManager;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Sodium;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.database.DB;
import io.pijun.george.database.Snapshot;
import retrofit2.Response;

public class BackupDatabaseWorker extends Worker {

    public BackupDatabaseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        L.i("BackupDB.doWork");
        Context ctx = getApplicationContext();
        if (!AuthenticationManager.isLoggedIn(ctx)) {
            return Result.success();
        }

        return uploadSnapshot();
    }

    @AnyThread
    public static void scheduleBackup(@NonNull Context ctx) {
        Constraints constraints = new Constraints.Builder().
                setRequiredNetworkType(NetworkType.CONNECTED).
                setRequiresCharging(false).
                setRequiresDeviceIdle(false).
                build();
        WorkRequest req = new OneTimeWorkRequest.Builder(BackupDatabaseWorker.class).
                setId(UUID.randomUUID()).
                setConstraints(constraints).
                setInitialDelay(Duration.ofSeconds(10)). // in case additional db operations will be performed after this request
                build();
        WorkManager.getInstance(ctx).enqueue(req);
    }

    @NonNull
    @WorkerThread
    private Result uploadSnapshot() {
        Context ctx = getApplicationContext();
        Snapshot snapshot = DB.get().getSnapshot();

        byte[] symmetricKey = Prefs.get(ctx).getSymmetricKey();
        String token = Prefs.get(ctx).getAccessToken();
        if (symmetricKey == null || TextUtils.isEmpty(token)) {
            return Result.success();
        }

        byte[] snapshotBytes = snapshot.toJson();
        if (snapshotBytes == null) {
            throw new RuntimeException("Snapshot data is null");
        }
        EncryptedData encData = Sodium.symmetricKeyEncrypt(snapshotBytes, symmetricKey);
        try {
            Response<Void> response = OscarClient.newInstance(token).saveDatabaseBackup(encData).execute();
            if (!response.isSuccessful()) {
                OscarError err = OscarError.fromResponse(response);
                L.w("Encrypted db backup failed: " + err);
                L.w("\terror from server: " + err);
                return Result.failure();
            } else {
                L.i("BackupDB completed");
                return Result.success();
            }
        } catch (IOException ex) {
            // network error
            L.w("\tsnapshot upload failed");
            return Result.failure();
        }
    }
}
