package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class MyLocationView extends View {

    public static void draw(Bitmap bitmap) {
        Paint fill = new Paint();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(65, 137, 238));
        fill.setAntiAlias(true);

        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.WHITE);
        border.setAntiAlias(true);

        draw(new Canvas(bitmap), fill, border);
    }

    public static void draw(@NonNull Canvas canvas, Paint fill, Paint border) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        float radius = (float)Math.min(width, height) / 2.0f;
        border.setStrokeWidth(radius/8.0f);
        canvas.drawCircle(width/2.0f, height/2.0f, radius, fill);
        canvas.drawCircle(width/2.0f, height/2.0f, radius-radius/16.0f, border);
    }

    public MyLocationView(Context context) {
        super(context);
    }

    public MyLocationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyLocationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyLocationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private Paint mFill;
    private Paint mBorder;

    private void init() {
        mFill = new Paint();
        mFill.setColor(Color.rgb(65, 137, 238));

        mBorder = new Paint();
        mBorder.setStyle(Paint.Style.STROKE);
        mBorder.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        MyLocationView.draw(canvas, mFill, mBorder);
    }
}
