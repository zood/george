package io.pijun.george.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.database.MovementType;

public class UserActivityReceiver extends BroadcastReceiver {

    private static final int TRANSITION_REQUEST_CODE = 3971;

    private static ActivityTransition createTransition(int activity, int transition) {
        return new ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(transition)
                .build();
    }

    @AnyThread
    @NonNull
    private static List<ActivityTransition> getHandledTransitions() {
        List<ActivityTransition> transitions = new ArrayList<>();
        int[] activities = new int[]{
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.ON_FOOT,
                DetectedActivity.RUNNING,
                DetectedActivity.WALKING,
                DetectedActivity.STILL,
        };

        for (int a : activities) {
            transitions.add(createTransition(a, ActivityTransition.ACTIVITY_TRANSITION_ENTER));
            transitions.add(createTransition(a, ActivityTransition.ACTIVITY_TRANSITION_EXIT));
        }

        return transitions;
    }

    @AnyThread
    @NonNull
    private static PendingIntent getPendingIntent(@NonNull Context context) {
        return PendingIntent.getBroadcast(context,
                TRANSITION_REQUEST_CODE,
                new Intent(context, UserActivityReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    @UiThread
    public void onReceive(Context context, Intent intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            if (result != null) {
                for (ActivityTransitionEvent evt : result.getTransitionEvents()) {
                    MovementType m = MovementType.getByDetectedActivity(evt.getActivityType());
                    int transition = evt.getTransitionType();
                    L.i("UAR: " + transition + " " + m.val);
                    Prefs prefs = Prefs.get(context);
                    MovementType currMovement = prefs.getCurrentMovement();
                    if (transition == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                        if (currMovement == m) {
                            prefs.setCurrentMovement(MovementType.Unknown);
                        }
                    } else if (transition == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        prefs.setCurrentMovement(m);
                    }
                }
            }
        }
    }

    @AnyThread
    public static void requestUpdates(@NonNull Context context) {
        ActivityTransitionRequest req = new ActivityTransitionRequest(getHandledTransitions());
        Task<Void> task = ActivityRecognition.getClient(context).requestActivityTransitionUpdates(req, getPendingIntent(context));
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                L.i("Successfully requested transition updates");
            }
        });
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                L.w("Failed to request activity transition updates", e);
            }
        });
    }

    @AnyThread
    public static void stopUpdates(@NonNull Context context) {
        Task<Void> task = ActivityRecognition.getClient(context).removeActivityTransitionUpdates(getPendingIntent(context));
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                L.i("Successfuly ");
            }
        });
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                L.w("Failed to stop activity transition updates", e);
            }
        });
    }

}
