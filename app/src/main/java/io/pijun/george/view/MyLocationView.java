package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import io.pijun.george.R;
import io.pijun.george.Utils;

public class MyLocationView extends View {

    public static Bitmap getBitmap(Context ctx) {
        int fortyFive = Utils.dpsToPix(ctx, 45);
        Bitmap bitmap = Bitmap.createBitmap(fortyFive, fortyFive, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float cx = fortyFive/2.0f;
        float cy = fortyFive/2.0f;
        int three = Utils.dpsToPix(ctx, 3);

        Paint blue = new Paint();
        blue.setStyle(Paint.Style.FILL);
        blue.setColor(0x8032b6f4);
        blue.setAntiAlias(true);
        blue.setShadowLayer(three, 0, 1, 0x33000000);
        float blueRadius = Utils.dpsToPix(ctx, 42)/2.0f;
        canvas.drawCircle(cx, cy, blueRadius, blue);

        Paint white = new Paint();
        white.setStyle(Paint.Style.FILL);
        white.setColor(Color.WHITE);
        white.setAntiAlias(true);
        white.setShadowLayer(3, 0, 1, 0x33000000);
        float whiteRadius = Utils.dpsToPix(ctx, 24)/2.0f;
        canvas.drawCircle(cx, cy, whiteRadius, white);

        Paint green = new Paint();
        green.setStyle(Paint.Style.FILL);
        green.setColor(ContextCompat.getColor(ctx, R.color.colorPrimary));
        green.setAntiAlias(true);
        float greenRadius = Utils.dpsToPix(ctx, 10)/2.0f;
        canvas.drawCircle(cx, cy, greenRadius, green);

        return bitmap;
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

    @Override
    protected void onDraw(Canvas canvas) {
    }
}
