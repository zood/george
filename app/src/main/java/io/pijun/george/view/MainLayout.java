package io.pijun.george.view;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.FragmentActivity;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
import android.view.animation.LinearInterpolator;

import java.lang.ref.WeakReference;

import io.pijun.george.FriendsSheetFragment;
import io.pijun.george.R;

public class MainLayout extends ConstraintLayout {
    private boolean mIsScrolling;
    @Nullable private Float yDown = null;
    private final int touchSlop;
    @NonNull private WeakReference<FriendsSheetBehavior> behaviorRef = new WeakReference<>(null);
    boolean sheetIsHidden = true;
    private VelocityTracker velocityTracker;

    public MainLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public MainLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public MainLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            if (mIsScrolling && yDown != null) {
                final float yDiff = ev.getY() - yDown;
                settleBottomSheet(yDiff);
            }
            mIsScrolling = false;
            return false; // Do not intercept touch event, let the child handle it.
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (mIsScrolling) {
                    return true;
                }

                if (yDown == null) {
                    return false;
                }
                final float yDiff = ev.getY() - yDown;
                if (Math.abs(yDiff) > touchSlop) {
                    mIsScrolling = true;
                    velocityTracker = VelocityTracker.obtain();
                    velocityTracker.addMovement(ev);
                    return true;
                }
            }
            break;
            case MotionEvent.ACTION_DOWN: {
                FriendsSheetBehavior behavior = behaviorRef.get();
                if (behavior == null) {
                    return false;
                }
                if (ev.getY() > behavior.sheetView.getY()) {
                    yDown = ev.getY();
                } else {
                    yDown = null;
                }
                return false;
            }
        }

        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        velocityTracker.addMovement(event);
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            if (mIsScrolling && yDown != null) {
                // check if this was a fling
                velocityTracker.computeCurrentVelocity(1000);
                float ySpeed = velocityTracker.getYVelocity();
                if (ySpeed < -1000) {
                    setBottomSheetState(true, ySpeed < -5000);
                } else if (ySpeed > 1000) {
                    setBottomSheetState(false, ySpeed > 5000);
                } else {
                    final float yDiff = event.getY() - yDown;
                    settleBottomSheet(yDiff);
                }
            }
            velocityTracker.recycle();
            velocityTracker = null;
            mIsScrolling = false;
            return false; // Do not intercept touch event, let the child handle it.
        }

        if (mIsScrolling && yDown != null) {
            final float yDiff = event.getY() - yDown;
            slideBottomSheet(yDiff);
            return true;
        }

        return false;
    }

    @CheckResult
    public static FriendsSheetBehavior registerFriendsSheet(@NonNull FriendsSheetFragment sheetFragment, @NonNull FriendsSheetLayout sheetView) {
        FragmentActivity activity = sheetFragment.requireActivity();
        View view = activity.findViewById(R.id.root);
        if (view == null) {
            throw new IllegalArgumentException("Unable to find the MainLayout view. Found null.");
        }
        if (!(view instanceof MainLayout)) {
            throw new IllegalArgumentException("Unable to find the MainLayout view. Found " + view);
        }
        FriendsSheetBehavior b = new FriendsSheetBehavior((MainLayout) view, sheetView, sheetFragment);
        ((MainLayout) view).behaviorRef = new WeakReference<>(b);
        return b;
    }

    void setBottomSheetState(boolean expanded, boolean quickly) {
        FriendsSheetBehavior behavior = behaviorRef.get();
        if (behavior == null) {
            return;
        }
        ViewPropertyAnimator animator = behavior.sheetView.animate();
        if (quickly) {
            animator.setInterpolator(new LinearInterpolator()).setDuration(100);
        } else {
            animator.setInterpolator(new LinearInterpolator()).setDuration(200);
        }
        float startTransY = behavior.sheetView.getTranslationY();
        float delta;
        if (expanded) {
            animator.translationY(0);
            delta = startTransY;
            sheetIsHidden = false;
        } else {
            animator.translationY(behavior.sheetView.hiddenStateTranslationY);
            delta = behavior.sheetView.getTranslationY() - behavior.sheetView.hiddenStateTranslationY;
            sheetIsHidden = true;
        }

        animator.setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                float animatedYSoFar = delta * fraction;
                float absYSoFar = startTransY - animatedYSoFar;
                float pos = 1.0f - absYSoFar / behavior.sheetView.hiddenStateTranslationY;
                behavior.fragment.onSlide(pos);
            }
        });
    }

    private void settleBottomSheet(float yDiff) {
        FriendsSheetBehavior behavior = behaviorRef.get();
        if (behavior == null) {
            return;
        }
        float transY = behavior.sheetView.hiddenStateTranslationY + yDiff * 1.25f;
        // make sure the value is between 0 and the hidden state value
        if (transY < 0) {
            transY = 0;
        } else if (transY > behavior.sheetView.hiddenStateTranslationY) {
            transY = behavior.sheetView.hiddenStateTranslationY;
        }
        float progress = 1.0f - transY / behavior.sheetView.hiddenStateTranslationY;
        if (progress > 0.5) {
            setBottomSheetState(true, false);
        } else {
            setBottomSheetState(false, false);
        }
    }

    private void slideBottomSheet(float yDiff) {
        FriendsSheetBehavior behavior = behaviorRef.get();
        if (behavior == null) {
            return;
        }
        float transY;
        if (sheetIsHidden) {
            transY = behavior.sheetView.hiddenStateTranslationY + yDiff * 1.25f;
        } else {
            transY = yDiff * 1.25f;
        }
        // make sure the value is between 0 and the hidden state value
        if (transY < 0) {
            transY = 0;
        } else if (transY > behavior.sheetView.hiddenStateTranslationY) {
            transY = behavior.sheetView.hiddenStateTranslationY;
        }
        behavior.sheetView.setTranslationY(transY);
        behavior.fragment.onSlide(1.0f - transY / behavior.sheetView.hiddenStateTranslationY);
    }
}
