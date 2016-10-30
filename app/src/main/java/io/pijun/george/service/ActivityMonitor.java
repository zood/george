package io.pijun.george.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognitionResult;

import io.pijun.george.L;

public class ActivityMonitor extends IntentService {

    private static int REQUEST_CODE = 5592;     // magic number

    public static PendingIntent getPendingIntent(Context context) {
        Intent i = new Intent(context, ActivityMonitor.class);
        return PendingIntent.getService(context, REQUEST_CODE, i, 0);
    }

    public ActivityMonitor() {
        super("ActivityMonitor");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        L.i("AM.onHandleIntent: " + result);
    }
}
