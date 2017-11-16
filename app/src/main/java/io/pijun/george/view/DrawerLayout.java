package io.pijun.george.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import io.pijun.george.R;

public class DrawerLayout extends ViewGroup {

    private View mContent;
    private boolean mHasInited = false;

    public DrawerLayout(Context context) {
        super(context);
    }

    public DrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void init() {
        mHasInited = true;

        int count = getChildCount();
        for (int i=0; i<count; i++) {
            View child = getChildAt(i);
            if (child.getId() == R.id.content) {
                mContent = child;
                break;
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!mHasInited) {
            init();
        }

        mContent.layout(l, t, r, b);
    }
}
