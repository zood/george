package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import io.pijun.george.Utils;
import xyz.zood.george.R;

public class MyLocationView {

    public static Bitmap getBitmap(@NonNull Context ctx) {
        int size = Utils.dpsToPix(ctx, 24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float cx = size/2.0f;
        float cy = size/2.0f;

        Paint white = new Paint();
        white.setStyle(Paint.Style.FILL);
        white.setColor(Color.WHITE);
        white.setAntiAlias(true);
        white.setShadowLayer(3, 0, 1, 0x33000000);
        float whiteRadius = Utils.dpsToPix(ctx, 24)/2.0f;
        canvas.drawCircle(cx, cy, whiteRadius, white);

        Paint green = new Paint();
        green.setStyle(Paint.Style.FILL);
        green.setColor(ContextCompat.getColor(ctx, R.color.zood_blue));
        green.setAntiAlias(true);
        float greenRadius = Utils.dpsToPix(ctx, 10)/2.0f;
        canvas.drawCircle(cx, cy, greenRadius, green);

        return bitmap;
    }
}
