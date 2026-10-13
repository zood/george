package xyz.zood.george.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

@SuppressWarnings("unused")
public class SlidableScrollView extends ScrollView {
    private float pendingXFraction = Float.NaN;

    public SlidableScrollView(Context context) {
        super(context);
    }

    public SlidableScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlidableScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SlidableScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
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
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        final int width = getWidth();
        if (!Float.isNaN(pendingXFraction) && width > 0) {
            setX(pendingXFraction * width);
            pendingXFraction = Float.NaN;
        }
    }
}
