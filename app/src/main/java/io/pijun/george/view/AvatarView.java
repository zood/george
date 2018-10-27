package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.res.ResourcesCompat;
import io.pijun.george.Constants;
import io.pijun.george.R;

public class AvatarView extends View implements Target {

    private static Typeface poppinsBold;

    private int height;
    private Bitmap image;
    private Paint imagePaint;
    private StaticLayout layout;
    private BitmapShader shader;
    private Paint bgPaint;
    private TextPaint textPaint;
    private int textWidth;
    private String username;
    private int width;

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

    @NonNull
    private static ColorPair getColorPair(byte val) {
        // we want the unsigned value
        int num = val & 0xFF;
        if (num < 42) {
            return new ColorPair(Color.parseColor("#5CD1FF"), Color.WHITE); // blue
        } else if (num < 85) {
            return new ColorPair(Color.parseColor("#E41C16"), Color.WHITE); // red
        } else if (num < 128) {
            return new ColorPair(Color.parseColor("#3C4466"), Color.WHITE); // navy
        } else if (num < 170) {
            return new ColorPair(Color.parseColor("#F6921E"), Color.WHITE); // orange
        } else if (num < 213) {
            return new ColorPair(Color.parseColor("#FFE422"), Color.parseColor("#46585E")); // canary
        } else {
            return new ColorPair(Color.parseColor("#46585E"), Color.WHITE); // grey
        }
    }

    private static byte[] getMD5Hash(@NonNull String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data.getBytes(Constants.utf8));
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("MD5 algo not found", ex);
        }
    }

    private void handleSizeChanged() {
        textPaint.setTextSize(height * 0.7f);
        if (!TextUtils.isEmpty(username)) {
            String txt = username.charAt(0) + "";
            textWidth = (int)textPaint.measureText(txt);
            if (Build.VERSION.SDK_INT >= 23) {
                layout = StaticLayout.Builder.obtain(txt, 0, 1, textPaint, textWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0, 1f)
                        .setIncludePad(false)
                        .build();
            } else {
                //noinspection deprecation
                layout = new StaticLayout(txt, textPaint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
            }
        } else {
            layout = null;
        }

        if (image != null) {
            float xScale = width / (float)image.getWidth();
            float yScale = height / (float)image.getHeight();

            Matrix matrix = new Matrix();
            matrix.postScale(xScale, yScale);
            shader.setLocalMatrix(matrix);
        }
    }

    @UiThread
    private void init() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, getWidth(), getHeight(), getWidth()/2.0f);
            }
        });
        setClipToOutline(true);

        imagePaint = new Paint();
        imagePaint.setAntiAlias(true);
        bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setStyle(Paint.Style.FILL);
        textPaint = new TextPaint();
        if (!isInEditMode()) {  // I don't know why font resources can't be loaded in edit mode
            if (poppinsBold == null) {
                poppinsBold = ResourcesCompat.getFont(getContext(), R.font.poppins_bold);
            }
            textPaint.setTypeface(poppinsBold);
        }
        textPaint.setAntiAlias(true);

        if (isInEditMode()) {
            setUsername("zood");
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (image == null && TextUtils.isEmpty(username)) {
            canvas.drawColor(Color.WHITE);
            return;
        }

        if (image == null) {
            // no image, so draw the letter
//            canvas.drawColor(colors.background);
            canvas.drawRoundRect(0, 0, width, height, width/2.0f, height/2.0f, bgPaint);
            canvas.save();
            canvas.translate((width - textWidth)/2.0f, 0);
            layout.draw(canvas);
            canvas.restore();
        } else {
            canvas.drawRoundRect(0, 0, width, height, width/2.0f, height/2.0f, imagePaint);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        width = w;
        height = h;
        handleSizeChanged();
    }

    public void setImage(@Nullable Bitmap img) {
        this.image = img;
        if (img == null) {
            imagePaint.setShader(null);
            shader = null;
        } else {
            shader = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            imagePaint.setShader(shader);
        }
        handleSizeChanged();
        invalidate();
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
        ColorPair colors = getColorPair(getMD5Hash(username)[15]);
        textPaint.setColor(colors.foreground);
        bgPaint.setColor(colors.background);
        image = null;
        imagePaint.setShader(null);

        handleSizeChanged();
        invalidate();
    }

    private static class ColorPair {
        @ColorInt
        final int foreground;
        @ColorInt
        final int background;

        ColorPair(@ColorInt int background, @ColorInt int foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    //region Picasso target

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        setImage(null);
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        setImage(bitmap);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {}

    //endregion

}
