package io.pijun.george.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import io.pijun.george.Identicon;
import io.pijun.george.R;

/*
base = 43dp
img-diameter = 34dp
border-width 2.5dp =
border-shadow dy=3dp, blur=5dp
shadow-color = border-color at 50% alpha
shadow size = 7%
 */

public class AvatarView extends View implements Target {

    private float mWidth;
    private float mHeight;
    private Paint mBorderPaint;
    private float mBorderWidth;
    @Nullable private Bitmap mImg;
    @Nullable private BitmapShader mImgShader;
    private Paint mImgPaint;
    private float mRadius;
    public String username;

    public AvatarView(Context context) {
        super(context);
        init(null);
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void handleSizeChanged() {
        mBorderWidth = mWidth * 0.07f;
        mBorderPaint.setStrokeWidth(mBorderWidth);
        mRadius = mWidth/2.0f - mBorderWidth/2.0f;

        if (mImg != null && mImgShader != null) {
            float xScale = (mRadius * 2 - mBorderWidth) / (float) mImg.getWidth();
            float yScale = (mRadius * 2 - mBorderWidth) / (float) mImg.getHeight();

            Matrix matrix = new Matrix();
            matrix.postScale(xScale, yScale);
            matrix.postTranslate(mBorderWidth, mBorderWidth);
            mImgShader.setLocalMatrix(matrix);
        }
    }

    private void init(@Nullable AttributeSet attrs) {
        int borderColor = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        if (attrs != null) {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarView, 0, 0);
            try {
                borderColor = a.getColor(R.styleable.AvatarView_borderColor, borderColor);
            } finally {
                a.recycle();
            }
        }

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(borderColor);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setAntiAlias(true);

        mImgPaint = new Paint();
        mImgPaint.setAntiAlias(true);

        if (isInEditMode()) {
            mImg = BitmapFactory.decodeResource(getResources(), R.drawable.george_clooney);
            mImgShader = new BitmapShader(mImg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mImgPaint.setShader(mImgShader);
        }

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, getWidth(), getHeight(), mRadius);
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(mWidth/2.0f, mHeight/2.0f, mRadius, mBorderPaint);
        //noinspection SuspiciousNameCombination
        canvas.drawRoundRect(mBorderWidth,
                mBorderWidth,
                mRadius * 2.0f,
                mRadius * 2.0f,
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
    public void setBorderColor(@ColorInt int color) {
        mBorderPaint.setColor(color);
        invalidate();
    }

    @UiThread
    public void setBorderColorRes(@ColorRes int color) {
        setBorderColor(ContextCompat.getColor(getContext(), color));
    }

    @UiThread
    public void setImage(@Nullable Bitmap img) {
        mImg = img;
        if (img == null) {
            mImgShader = null;
            mImgPaint.setShader(null);
        } else {
            mImgShader = new BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mImgPaint.setShader(mImgShader);
        }
        handleSizeChanged();
        invalidate();
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        setImage(bitmap);
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        // load the identicon instead
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Identicon.draw(bmp, username);
        setImage(bmp);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {}
}
