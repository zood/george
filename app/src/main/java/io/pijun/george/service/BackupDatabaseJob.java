package io.pijun.george.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.text.format.DateUtils;

import java.io.IOException;

import io.pijun.george.App;
import io.pijun.george.DB;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Sodium;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.models.Snapshot;
import retrofit2.Response;

public class BackupDatabaseJob extends JobService {

    private static final int JOB_ID = 2194; // made up number

    public static JobInfo getJobInfo(Context context) {
        ComponentName compName = new ComponentName(context, BackupDatabaseJob.class);
        // so any additional db operations complete
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, compName)
                .setMinimumLatency(10 * DateUtils.SECOND_IN_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);
        return builder.build();
    }

    private JobParameters mJobParams;
    private volatile boolean mShouldStop = false;

    @Override
    public boolean onStartJob(JobParameters params) {
        L.i("BackupDatabaseJob.onStartJob");
        if (!Prefs.get(this).isLoggedIn()) {
            jobFinished(params, false);
            return false;
        }

        mJobParams = params;

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                uploadSnapshot();
            }
        });
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        L.i("BackupDatabaseJob.onStopJob");
        mShouldStop = false;
        return false;
    }

    @WorkerThread
    private void uploadSnapshot() {
        L.i("BackupDatabaseJob.uploadSnapshot");
        Snapshot snapshot = DB.get(this).getSnapshot();
        if (mShouldStop) {
            return;
        }

        byte[] symmetricKey = Prefs.get(this).getSymmetricKey();
        String token = Prefs.get(this).getAccessToken();
        if (symmetricKey == null || TextUtils.isEmpty(token)) {
            jobFinished(mJobParams, false);
            return;
        }

        EncryptedData encData = Sodium.symmetricKeyEncrypt(snapshot.toJson(), symmetricKey);
        if (mShouldStop) {
            return;
        }
        try {
            Response<Void> response = OscarClient.newInstance(token).saveDatabaseBackup(encData).execute();
            if (mShouldStop) {
                return;
            }

            if (!response.isSuccessful()) {
                OscarError err = OscarError.fromResponse(response);
                L.w("Encrypted db backup failed: " + err);
                jobFinished(mJobParams, true);
            } else {
                L.i("|  successfully uploaded snapshot");
                jobFinished(mJobParams, false);
            }
        } catch (IOException ex) {
            // network error
            L.w("|  snapshot upload failed");
            if (mShouldStop) {
                return;
            }

            jobFinished(mJobParams, true);
        }
    }
}
