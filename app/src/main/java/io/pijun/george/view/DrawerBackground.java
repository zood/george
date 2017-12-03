package io.pijun.george.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import io.pijun.george.R;
import io.pijun.george.Utils;

public class DrawerBackground extends View {

    private Paint mPaint;
    private float mCenterX;
    private float mCenterY;
    private float mRadius;

    public DrawerBackground(Context context) {
        super(context);
        init();
    }

    public DrawerBackground(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawerBackground(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.WHITE);
        setWillNotDraw(false);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCenterX = (float)w * 0.25f;
        float exposed = Utils.dpsToPix(getContext(), 200); //float)h * 0.40f;
        // radius of the circle should be 3 times the exposed portion
        mRadius = exposed * 3.0f;
        mCenterY = -exposed * 2.0f;

        int colorStart = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        int colorEnd = ContextCompat.getColor(getContext(), R.color.drawer_gradient_end);
        LinearGradient lg = new LinearGradient(0, exposed, w, 0, colorStart, colorEnd, Shader.TileMode.MIRROR);
        mPaint.setShader(lg);
    }
}
