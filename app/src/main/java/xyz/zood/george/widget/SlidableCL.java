package xyz.zood.george.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.constraintlayout.widget.ConstraintLayout;

@SuppressWarnings("unused")
public class SlidableCL extends ConstraintLayout {
    private float pendingXFraction = Float.NaN;

    public SlidableCL(Context context) {
        super(context);
    }

    public SlidableCL(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlidableCL(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public float getXFraction() {
        final int width = getWidth();
        if (width != 0) {
            return getX() / getWidth();
        }
        return getX();
    }

    public void setXFraction(float xFraction) {
        final int width = getWidth();
        if (width > 0) {
            pendingXFraction = Float.NaN;
            setX(xFraction * width);
        } else {
            // Not laid out yet, so apply it in onLayout. The animation may even finish before the
            // first layout (e.g. when system animations are disabled).
            pendingXFraction = xFraction;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final int width = getWidth();
        if (!Float.isNaN(pendingXFraction) && width > 0) {
            setX(pendingXFraction * width);
            pendingXFraction = Float.NaN;
        }
    }
}
