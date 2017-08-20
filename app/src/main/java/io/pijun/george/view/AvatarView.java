package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import io.pijun.george.R;

/*
base = 43dp
img-diameter = 34dp
border-width 2.5dp =
border-shadow dy=3dp, blur=5dp
shadow-color = border-color at 50% alpha
shadow size = 7%
 */

public class AvatarView extends View {

    private float mWidth;
    private float mHeight;
    private Paint mBorderPaint;
    private float mBorderWidth;
    private Bitmap mImg;
    private BitmapShader mImgShader;
    private Paint mImgPaint;
    private float mRadius;
    private Paint mBlackPaint;

    private Paint mShadowPaint;

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void handleSizeChanged() {
        mBorderWidth = mWidth * 0.07f;
        mBorderPaint.setStrokeWidth(mBorderWidth);
        mRadius = mWidth/2.0f - mBorderWidth/2.0f;

        float xScale = (mRadius*2-mBorderWidth) / (float)mImg.getWidth();
        float yScale = (mRadius*2-mBorderWidth) / (float)mImg.getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(xScale, yScale);
        matrix.postTranslate(mBorderWidth, mBorderWidth);
        mImgShader.setLocalMatrix(matrix);
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        mBlackPaint = new Paint();
        mBlackPaint.setColor(Color.BLACK);

        mShadowPaint = new Paint();
        mShadowPaint.setColor(0x80000000);
        mShadowPaint.setStyle(Paint.Style.STROKE);
        mShadowPaint.setAntiAlias(true);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorPrimary));
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setAntiAlias(true);

        mImg = BitmapFactory.decodeResource(getResources(), R.drawable.george_clooney);
        mImgShader = new BitmapShader(mImg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        mImgPaint = new Paint();
        mImgPaint.setAntiAlias(true);
        mImgPaint.setShader(mImgShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

//        canvas.drawRect(0, 0, mWidth, mHeight, mBlackPaint);
        canvas.drawCircle(mWidth/2.0f, mHeight/2.0f, mRadius, mBorderPaint);
        canvas.drawRoundRect(mBorderWidth,
                mBorderWidth,
                mRadius*2.0f,
                mRadius*2.0f,
                mRadius,
                mRadius,
                mImgPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        handleSizeChanged();
    }

    @UiThread
    public void setBorderColor(@ColorRes int color) {
        mBorderPaint.setColor(ContextCompat.getColor(getContext(), color));
        invalidate();
    }

    @UiThread
    public void setImage(@NonNull Bitmap img) {
        mImg = img;
        mImgShader = new BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mImgPaint.setShader(mImgShader);
        handleSizeChanged();
        invalidate();
    }
}
