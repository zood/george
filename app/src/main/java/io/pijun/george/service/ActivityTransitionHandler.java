package io.pijun.george.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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

import io.pijun.george.L;
import io.pijun.george.database.MovementType;

public class ActivityTransitionHandler extends IntentService {

    private static final int TRANSITION_REQUEST_CODE = 3971;
    @Nullable private static MovementType currentMovement = MovementType.Unknown;

    private static Intent newIntent(@NonNull Context context) {
        return new Intent(context, ActivityTransitionHandler.class);
    }

    public ActivityTransitionHandler() {
        super("ActivityTransitionServiceThread");
    }

    private static ActivityTransition createTransition(int activity, int transition) {
        return new ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(transition)
                .build();
    }

    @AnyThread @NonNull
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

    @Nullable @AnyThread
    public static MovementType getCurrentMovement() {
        return currentMovement;
    }

    @AnyThread @NonNull
    private static PendingIntent getPendingIntent(@NonNull Context context) {
        return PendingIntent.getService(context,
                TRANSITION_REQUEST_CODE,
                newIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        L.i("movement changed");
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            if (result != null) {
                for (ActivityTransitionEvent evt : result.getTransitionEvents()) {
                    MovementType m = MovementType.getByDetectedActivity(evt.getActivityType());
                    int transition = evt.getTransitionType();
                    L.i("\t" + transition + " " + m.val);
                    if (transition == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                        if (currentMovement == m) {
                            currentMovement = MovementType.Unknown;
                        }
                    } else if (transition == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        currentMovement = m;
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
