package io.pijun.george.view;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

public final class DrawerActionRecognizer extends GestureDetector.SimpleOnGestureListener {
    private boolean isClosed = true;
    private final float screenWidth;
    private boolean isGesturing = false;
    @NonNull
    private DrawerSwipesListener mListener;

    public DrawerActionRecognizer(float screenWidth, @NonNull DrawerSwipesListener l) {
        this.screenWidth = screenWidth;
        this.mListener = l;
    }

    public boolean isGesturing() {
        return isGesturing;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        // if we're already gesturing, don't track additional fingers
        if (isGesturing) {
            return false;
        }
        if (isClosed) {
            // make sure it starts at the edge of the screen
            if (e.getX() < screenWidth * 0.03) {
                isGesturing = true;
                return true;
            }
            return false;
        } else {
            // always respond when the drawer is open
            isGesturing = true;
            return true;
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//            L.i("fling - vX: " + velocityX + ", vY: " + velocityY);
        if (velocityX > 200) {
            mListener.onFlingOpenDrawer();
            isClosed = false;
        } else if (velocityX < -200) {
            mListener.onFlingCloseDrawer();
            isClosed = true;
        } else {
            isClosed = !mListener.onSettleDrawer();
        }
        isGesturing = false;

        return true;
    }

    @Override
    public boolean onScroll(MotionEvent down, MotionEvent curr, float distanceX, float distanceY) {
        if (isClosed) {
            float dx = curr.getX() - down.getX();
            dx = Math.max(0, dx);
            mListener.onOpenDrawer(dx);
        } else {
            float dx = down.getX() - curr.getX();
            dx = Math.max(0, dx);
            mListener.onCloseDrawer(dx, distanceX);
        }

        return true;
    }

    public void onUp() {
        isClosed = !mListener.onSettleDrawer();
        isGesturing = false;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }
}
