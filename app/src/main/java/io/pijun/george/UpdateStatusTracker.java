package io.pijun.george;

import android.support.annotation.AnyThread;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.text.format.DateUtils;
import android.util.LongSparseArray;

import java.util.HashSet;

import io.pijun.george.api.UserComm;

public class UpdateStatusTracker {

    enum State {
        NotRequested,
        Requested,
        RequestedAndUnresponsive,
        RequestAcknowledged,
        RequestDenied,
        RequestFulfilled,
    }

    static class Response {
        final long responseTime;
        @UserComm.LocationUpdateRequestAction
        final String action;

        Response(long responseTime, @UserComm.LocationUpdateRequestAction String action) {
            this.responseTime = responseTime;
            this.action = action;
        }
    }

    private static HashSet<Listener> listeners = new HashSet<>();
    private static LongSparseArray<Long> requestTimes = new LongSparseArray<>();
    private static LongSparseArray<Response> responses = new LongSparseArray<>();

    public static void addListener(@NonNull Listener l) {
        synchronized (UpdateStatusTracker.class) {
            listeners.add(l);
        }
    }

    @CheckResult @AnyThread
    public static State getFriendState(long friendId) {
        long now = System.currentTimeMillis();
        Long reqTime = requestTimes.get(friendId);
        // if this is old data, remove it
        if (reqTime != null && (now - reqTime) > 2 * DateUtils.MINUTE_IN_MILLIS ) {
            requestTimes.remove(friendId);
            reqTime = null;
        }

        if (reqTime == null) {
            return State.NotRequested;
        }

        // check if we have a response
        Response resp = responses.get(friendId);
        // if this is old response data, remove it
        if (resp != null && (now - resp.responseTime) > 2 * DateUtils.MINUTE_IN_MILLIS) {
            responses.remove(friendId);
            // a 2 minute old response is no longer relevant
            resp = null;
        }
        if (resp == null) {
            // no response, so let's check if the request was sent recently
            long sinceReq = now - reqTime;
            if (sinceReq < 15000) {
                return State.Requested;
            } else if (sinceReq < 60000) {
                return State.RequestedAndUnresponsive;
            }
            return State.NotRequested;
        }

        if (resp.action.equals(UserComm.LOCATION_UPDATE_REQUEST_ACTION_STARTING)) {
            return State.RequestAcknowledged;
        } else if (resp.action.equals(UserComm.LOCATION_UPDATE_REQUEST_ACTION_FINISHED)) {
            return State.RequestFulfilled;
        }
        if (!resp.action.equals(UserComm.LOCATION_UPDATE_REQUEST_ACTION_TOO_SOON)) {
            L.i("Returning friendstate denied, but response action is " + resp.action);
        }

        return State.RequestDenied;
    }

    @AnyThread
    private static void notifyListeners(long friendId) {
        // we don't need to synchronize here, because we're only called from already
        // synchronized methods
        for (Listener l : listeners) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    l.onUpdateStatusChanged(friendId);
                }
            });
        }
    }

    @AnyThread
    public static void removeListener(@NonNull Listener l) {
        synchronized (UpdateStatusTracker.class) {
            listeners.remove(l);
        }
    }

    public static void setLastRequestTime(long friendId, long reqTime) {
        synchronized (UpdateStatusTracker.class) {
            requestTimes.put(friendId, reqTime);
            notifyListeners(friendId);
        }
    }

    @AnyThread
    public static void setUpdateRequestResponse(long friendId, long respTime, String message) {
        synchronized (UpdateStatusTracker.class) {
            responses.put(friendId, new Response(respTime, message));
            notifyListeners(friendId);
        }
    }

    public interface Listener {
        @UiThread
        void onUpdateStatusChanged(long friendId);
    }

}
