package io.pijun.george.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.List;

import io.pijun.george.App;
import io.pijun.george.event.MovementsUpdated;
import io.pijun.george.models.MovementType;

public class UserActivityReceiver extends IntentService {

    private static int REQUEST_CODE = 5592;     // magic number

    public static PendingIntent getPendingIntent(Context context) {
        Intent i = new Intent(context, UserActivityReceiver.class);
        return PendingIntent.getService(context, REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public UserActivityReceiver() {
        super("UserActivityReceiver");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        List<DetectedActivity> probableActivities = result.getProbableActivities();
        ArrayList<MovementType> movements = new ArrayList<>();
        for (DetectedActivity da : probableActivities) {
            if (da.getConfidence() > 70) {
                MovementType mt = MovementType.getByDetectedActivity(da.getType());
                if (mt != MovementType.Unknown) {
                    continue;
                }
                movements.add(mt);
            }
        }
        App.postOnBus(new MovementsUpdated(movements));
    }
}
