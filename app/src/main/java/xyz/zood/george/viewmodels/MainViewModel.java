package xyz.zood.george.viewmodels;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Timer;
import java.util.TimerTask;

import io.pijun.george.database.FriendRecord;
import io.pijun.george.service.TimedShareService;

public class MainViewModel extends ViewModel implements TimedShareService.Listener {

    private Timer countdownTimer;
    private final MutableLiveData<Event<Boolean>> liveOnAddFriendClicked = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> liveOnCloseTimedSheet = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> liveOnTimedShareClicked = new MutableLiveData<>();
    private final MutableLiveData<FriendRecord> liveSelectedFriend = new MutableLiveData<>();
    private MutableLiveData<Boolean> liveTimedShareIsRunning;
    private MutableLiveData<Long> liveTimedShareTimeRemaining;
    private MutableLiveData<String> liveTimedShareUrl;
    private long shareStartTime;
    private long shareDuration;
    private TimedShareListener timedShareListener;
    private boolean isBackgroundLocationRationaleVisible;
    private boolean isForegroundLocationRationaleVisible;
    private boolean isPreQLocationRationaleVisible;
    private boolean isTimedShareSheetDismissable;
    private boolean isActivityRecognitionRationaleVisible;

    private long calculateTimedShareRemaining(long now) {
        long hasBeen = now - shareStartTime;
        return shareDuration - hasBeen;
    }

    @UiThread
    public LiveData<Event<Boolean>> getOnAddFriendClicked() {
        return liveOnAddFriendClicked;
    }

    @UiThread
    public LiveData<Event<Boolean>> getOnCloseTimedSheet() {
        return liveOnCloseTimedSheet;
    }

    @UiThread
    public LiveData<Event<Boolean>> getOnTimedShareClicked() {
        return liveOnTimedShareClicked;
    }

    @UiThread
    public LiveData<FriendRecord> getSelectedFriend() {
        return liveSelectedFriend;
    }

    public long getTimedShareDuration() {
        return shareDuration;
    }

    @UiThread
    public LiveData<Boolean> getTimedShareIsRunning() {
        if (timedShareListener == null) {
            setupTimedShareListener();
        }

        return liveTimedShareIsRunning;
    }

    @UiThread
    public LiveData<Long> getTimedShareTimeRemaining() {
        if (timedShareListener == null) {
            setupTimedShareListener();
        }
        return liveTimedShareTimeRemaining;
    }

    public LiveData<String> getTimedShareUrl() {
        if (timedShareListener == null) {
            setupTimedShareListener();
        }
        return liveTimedShareUrl;
    }

    @AnyThread
    private void handleTimedShareFinished() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        shareDuration = 0;
        shareStartTime = 0;
        liveTimedShareTimeRemaining.postValue(0L);
        liveTimedShareIsRunning.postValue(false);
        liveTimedShareUrl.postValue(null);
    }

    @AnyThread
    private void handleTimedShareStarted(long startTime, long duration) {
        shareStartTime = startTime;
        shareDuration = duration;
        liveTimedShareIsRunning.postValue(true);
        countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long remaining = calculateTimedShareRemaining(System.currentTimeMillis());
                liveTimedShareTimeRemaining.postValue(remaining);
            }
        }, 0, 1000);
        TimedShareService svc = TimedShareService.get();
        if (svc != null) {
            liveTimedShareUrl.postValue(svc.getUrl());
        }
    }

    @UiThread
    public boolean isActivityRecognitionRationaleVisible() {
        return isActivityRecognitionRationaleVisible;
    }

    @UiThread
    public boolean isBackgroundLocationRationaleVisible() {
        return isBackgroundLocationRationaleVisible;
    }

    @UiThread
    public boolean isForegroundLocationRationaleVisible() {
        return isForegroundLocationRationaleVisible;
    }

    @UiThread
    public boolean isPreQLocationRationaleVisible() {
        return isPreQLocationRationaleVisible;
    }

    @UiThread
    public boolean isTimedShareSheetDismissable() {
        return isTimedShareSheetDismissable;
    }

    @UiThread
    public void notifyAddFriendClicked() {
        liveOnAddFriendClicked.setValue(new Event<>(true));
    }

    @UiThread
    public void notifyTimedShareClicked() {
        liveOnTimedShareClicked.setValue(new Event<>(true));
    }

    @Override
    protected void onCleared() {
        if (timedShareListener != null) {
            TimedShareService.removeListener(timedShareListener);
            timedShareListener = null;
        }
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    @UiThread
    public void onCloseTimedSheetAction() {
        liveOnCloseTimedSheet.setValue(new Event<>(true));
    }

    @UiThread
    public void selectFriend(@Nullable FriendRecord friend) {
        liveSelectedFriend.setValue(friend);
    }

    @UiThread
    public void setActivityRecognitionRationaleVisible(boolean visible) {
        this.isActivityRecognitionRationaleVisible = visible;
    }

    @UiThread
    public void setBackgroundLocationRationaleVisible(boolean visible) {
        this.isBackgroundLocationRationaleVisible = visible;
    }

    @UiThread
    public void setForegroundLocationRationaleVisible(boolean visible) {
        this.isForegroundLocationRationaleVisible = visible;
    }

    @UiThread
    public void setPreQLocationRationaleVisible(boolean visible) {
        this.isPreQLocationRationaleVisible = visible;
    }

    @UiThread
    public void setTimedShareSheetDismissable(boolean dismissable) {
        this.isTimedShareSheetDismissable = dismissable;
    }

    @UiThread
    private void setupTimedShareListener() {
        liveTimedShareIsRunning = new MutableLiveData<>();
        liveTimedShareTimeRemaining = new MutableLiveData<>();
        liveTimedShareUrl = new MutableLiveData<>();

        timedShareListener = new TimedShareListener();
        TimedShareService.addListener(timedShareListener);

        TimedShareService svc = TimedShareService.get();
        if (svc == null) {
            handleTimedShareFinished();
        } else {
            if (svc.isRunning()) {
                handleTimedShareStarted(svc.getStartTime(), svc.getShareDuration());
            } else {
                handleTimedShareFinished();
            }
        }
    }

    //region Timed share service listener

    private class TimedShareListener implements TimedShareService.Listener {
        @Override
        public void onTimedShareDurationChanged(long duration) {
            shareDuration = duration;
            shareStartTime = System.currentTimeMillis();
            long remaining = calculateTimedShareRemaining(System.currentTimeMillis());
            liveTimedShareTimeRemaining.postValue(remaining);
        }

        @Override
        public void onTimedShareFinished() {
            handleTimedShareFinished();
        }

        @Override
        public void onTimedShareStarted(long startTime, long duration) {
            handleTimedShareStarted(startTime, duration);
        }
    }

    //endregion

}
