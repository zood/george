package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import io.pijun.george.AvatarManager;
import io.pijun.george.Constants;
import io.pijun.george.R;

/**
 * This does the heavy lifting for the AvatarView class. The functionality was separated out of the View
 * class, so it could also be leveraged off the main thread for rendering the icon used for Google Maps
 * markers.
 */
public class AvatarRenderer {

    private static Typeface poppinsBold;

    private float diameter;
    private float radius;
    private Bitmap image;
    private Paint imagePaint;
    private Paint bgPaint;
    private StaticLayout layout;
    private BitmapShader shader;
    private TextPaint textPaint;
    private int textWidth;
    private String username;

    @AnyThread
    AvatarRenderer(@NonNull Context ctx, boolean isInEditMode) {
        imagePaint = new Paint();
        imagePaint.setAntiAlias(true);
        bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setStyle(Paint.Style.FILL);
        textPaint = new TextPaint();
        if (!isInEditMode) {  // I don't know why font resources can't be loaded in edit mode
            if (poppinsBold == null) {
                poppinsBold = ResourcesCompat.getFont(ctx, R.font.poppins_bold);
            }
            textPaint.setTypeface(poppinsBold);
        }
        textPaint.setAntiAlias(true);

        if (isInEditMode) {
            setUsername("zood");
        }
    }

    @AnyThread
    void draw(@NonNull Canvas canvas) {
        if (image == null && TextUtils.isEmpty(username)) {
            canvas.drawColor(Color.WHITE);
            return;
        }

        if (image == null) {
            // no image, so draw the letter
            canvas.drawRoundRect(0, 0, diameter, diameter, radius, radius, bgPaint);
            canvas.save();
            canvas.translate((diameter - textWidth)/2.0f, 0);
            layout.draw(canvas);
            canvas.restore();
        } else {
            canvas.drawRoundRect(0, 0, diameter, diameter, radius, radius, imagePaint);
        }
    }

    @AnyThread
    @NonNull
    public static BitmapDescriptor getBitmapDescriptor(@NonNull Context ctx, @NonNull String username, @DimenRes int sizeRes) {
        int size = ctx.getResources().getDimensionPixelSize(sizeRes);
        Bitmap img = null;
        try {
            img = Picasso.with(ctx).load(AvatarManager.getAvatar(ctx, username)).resize(size, size).get();
        } catch (IOException ignore) {}

        AvatarRenderer renderer = new AvatarRenderer(ctx, false);
        renderer.setUsername(username);
        renderer.setDiameter(size);
        if (img != null) {
            renderer.setImage(img);
        }

        Bitmap avatar = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(avatar);
        renderer.draw(c);

        return BitmapDescriptorFactory.fromBitmap(avatar);
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

    @AnyThread
    private static byte[] getMD5Hash(@NonNull String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data.getBytes(Constants.utf8));
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("MD5 algo not found", ex);
        }
    }

    private void recalculateDrawVariables() {
        textPaint.setTextSize(diameter * 0.7f);
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
            float xScale = diameter / (float)image.getWidth();
            float yScale = diameter / (float)image.getHeight();

            Matrix matrix = new Matrix();
            matrix.postScale(xScale, yScale);
            shader.setLocalMatrix(matrix);
        }
    }

    @AnyThread
    void setDiameter(float diameter) {
        this.diameter = diameter;
        this.radius = diameter / 2.0f;

        recalculateDrawVariables();
    }

    void setImage(@Nullable Bitmap img) {
        this.image = img;
        if (img == null) {
            imagePaint.setShader(null);
            shader = null;
        } else {
            shader = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            imagePaint.setShader(shader);
        }

        recalculateDrawVariables();
    }

    @AnyThread
    public void setUsername(@NonNull String username) {
        this.username = username;
        ColorPair colors = getColorPair(getMD5Hash(username)[15]);
        textPaint.setColor(colors.foreground);
        bgPaint.setColor(colors.background);
        image = null;
        imagePaint.setShader(null);

        recalculateDrawVariables();
    }

    private static class ColorPair {
        @ColorInt
        final int foreground;
        @ColorInt
        final int background;

        @AnyThread
        ColorPair(@ColorInt int background, @ColorInt int foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

}
