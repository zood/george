package io.pijun.george.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import io.pijun.george.R;
import io.pijun.george.Utils;

public class Hill extends View {

    private Paint mPaint;
    int mWidth, mHeight;
    int mArcLeft, mArcRight, mArcTop, mArcBottom;

    public Hill(Context context) {
        super(context);
        init();
    }

    public Hill(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Hill(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public Hill(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int color = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        mPaint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(mArcLeft, mArcTop, mArcRight, mArcBottom, 180, 180, true, mPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;

        int fiveForty = Utils.dpsToPix(getContext(), 540);
        int remainder = (fiveForty - mWidth)/2;
        mArcLeft = -remainder;
        mArcRight = mWidth + remainder;
        mArcTop = 0;
        mArcBottom = mHeight * 2;

    }
}
