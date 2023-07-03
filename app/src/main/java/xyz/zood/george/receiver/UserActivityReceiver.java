package xyz.zood.george.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.database.MovementType;
import xyz.zood.george.Permissions;

public class UserActivityReceiver extends BroadcastReceiver {

    private static final int TRANSITION_REQUEST_CODE = 3971;

    private static final Handler handler;
    private static boolean isRunning = false;
    static {
        HandlerThread thread = new HandlerThread(UserActivityReceiver.class.getSimpleName());
        thread.start();
        handler = new Handler(thread.getLooper());
    }

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
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    @WorkerThread
    private static void _requestUpdates(@NonNull Context context) {
        if (isRunning) {
            return;
        }
        if (!Permissions.checkGrantedActivityRecognitionPermission(context)) {
            L.i("UserActivityReceiver.requestUpdates is bailing");
            return;
        }

        ActivityTransitionRequest req = new ActivityTransitionRequest(getHandledTransitions());
        Task<Void> task = ActivityRecognition.getClient(context).requestActivityTransitionUpdates(req, getPendingIntent(context));
        try {
            Tasks.await(task);
            isRunning = true;
            L.i("Successfully requested transition updates");
        } catch (ExecutionException e) {
            L.w("Failed to request activity transition updates", e);
        } catch (InterruptedException e) {
            L.w("Interrupted when requesting activity transition updates", e);
        }
    }

    @AnyThread
    public static void requestUpdates(@NonNull Context context) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                _requestUpdates(context);
            }
        });
    }

    @WorkerThread
    private static void _stopUpdates(@NonNull Context context) {
        if (!isRunning) {
            return;
        }

        Task<Void> task = ActivityRecognition.getClient(context).removeActivityTransitionUpdates(getPendingIntent(context));
        try {
            Tasks.await(task);
            isRunning = false;
            L.i("Successfully stopped activity transition updates");
        } catch (ExecutionException e) {
            L.w("Failed to stop activity transition updates", e);
        } catch (InterruptedException e) {
            L.w("Interrupted while stopping activity transition updates", e);
        }
    }

    @AnyThread
    public static void stopUpdates(@NonNull Context context) {
        handler.post(new WorkerRunnable() {
            @Override
            public void run() {
                _stopUpdates(context);
            }
        });
    }

}
