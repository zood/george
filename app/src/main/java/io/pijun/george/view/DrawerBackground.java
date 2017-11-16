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

public class DrawerBackground extends View {

    private Paint mPaint;

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
        int colorPrimary = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        LinearGradient lg = new LinearGradient(0, 300, getWidth(), 0, colorPrimary, Color.GREEN, Shader.TileMode.MIRROR);
        mPaint.setShader(lg);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

//        canvas.drawCircle();
    }
}
