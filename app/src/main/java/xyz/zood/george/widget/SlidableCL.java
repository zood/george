package xyz.zood.george.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.constraintlayout.widget.ConstraintLayout;

@SuppressWarnings("unused")
public class SlidableCL extends ConstraintLayout {
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
        setX((width > 0) ? (xFraction * width) : -9999);
    }
}
