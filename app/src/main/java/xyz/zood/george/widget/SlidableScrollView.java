package xyz.zood.george.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

@SuppressWarnings("unused")
public class SlidableScrollView extends ScrollView {
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
        setX((width > 0) ? (xFraction * width) : -9999);
    }
}
