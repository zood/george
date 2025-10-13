package xyz.zood.george.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;

import xyz.zood.george.R;

public class CircularProgressView extends View {

    private float radius;
    private float strokeWidth;
    private float width;
    private float height;
    private Paint disabledPaint;
    private Paint remainingPaint;
    private Paint burnedPaint;
    private float timeRemaining = 1f;

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @UiThread
    private void init() {
        Context ctx = getContext();
        strokeWidth = ctx.getResources().getDimension(R.dimen.ten);
        disabledPaint = new Paint();
        disabledPaint.setAntiAlias(true);
        disabledPaint.setStyle(Paint.Style.STROKE);
        disabledPaint.setColor(ContextCompat.getColor(ctx, R.color.black_20));
        disabledPaint.setStrokeWidth(strokeWidth);

        remainingPaint = new Paint();
        remainingPaint.setAntiAlias(true);
        remainingPaint.setStyle(Paint.Style.STROKE);
        remainingPaint.setColor(Color.WHITE);
        remainingPaint.setStrokeWidth(strokeWidth);

        burnedPaint = new Paint();
        burnedPaint.setAntiAlias(true);
        burnedPaint.setStyle(Paint.Style.STROKE);
        burnedPaint.setColor(ContextCompat.getColor(ctx, R.color.zood_blue_light));
        burnedPaint.setStrokeWidth(strokeWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float halfStroke = strokeWidth/2;
        float burnedSweepAngle = 360.0f * (1.0f - timeRemaining);
        float remainingSweepAngle = 360.0f * timeRemaining;
        if (isEnabled()) {
            canvas.drawArc(halfStroke, halfStroke, width-halfStroke, height-halfStroke, -90.0f, burnedSweepAngle, false, burnedPaint);
            canvas.drawArc(halfStroke, halfStroke, width-halfStroke, height-halfStroke,-90.0f+burnedSweepAngle, remainingSweepAngle, false, remainingPaint);
            return;
        }
        canvas.drawRoundRect(halfStroke, halfStroke,width-halfStroke, height-halfStroke, radius-halfStroke, radius-halfStroke, disabledPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = w;
        height = h;
        radius = (float)(w/2);   // let's hope it's square
    }

    public void setTimeRemaining(@FloatRange(fromInclusive = true, from = 0.0, toInclusive = true, to = 1.0) float tr) {
        if (this.timeRemaining == tr) {
            return;
        }
        this.timeRemaining = tr;
        invalidate();
    }
}










