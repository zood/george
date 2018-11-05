package xyz.zood.george.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.view.GestureDetectorCompat;

public class RadialMenuButton extends androidx.appcompat.widget.AppCompatImageView {

    private GestureDetectorCompat detector;
    private boolean isDown = false;
    @Nullable private OnPressListener listener;

    public RadialMenuButton(Context context) {
        super(context);
        init();
    }

    public RadialMenuButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RadialMenuButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        detector = new GestureDetectorCompat(getContext(), gestureListener);
        detector.setIsLongpressEnabled(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDown && event.getAction() == MotionEvent.ACTION_UP) {
            isDown = false;
            if (listener != null) {
                listener.onRadialButtonUp();
            }
        }

        if (detector.onTouchEvent(event)) {
            return true;
        }

        return super.onTouchEvent(event);
    }

    @UiThread
    public void setOnPressListener(@Nullable OnPressListener l) {
        this.listener = l;
    }

    private GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            isDown = true;
            if (listener != null) {
                listener.onRadialButtonDown();
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            isDown = false;
            if (listener != null) {
                listener.onRadialButtonUp();
            }
            performClick();
            return true;
        }
    };

    interface OnPressListener {
        void onRadialButtonDown();
        void onRadialButtonUp();
    }
}
