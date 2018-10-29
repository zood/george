package io.pijun.george.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

public class AvatarView extends View implements Target {

    private AvatarRenderer renderer;

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

    @UiThread
    private void init() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, getWidth(), getHeight(), getWidth()/2.0f);
            }
        });
        setClipToOutline(true);

        renderer = new AvatarRenderer(getContext(), isInEditMode());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        renderer.draw(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        renderer.setDiameter(w);
        invalidate();
    }

    public void setImage(@Nullable Bitmap img) {
        renderer.setImage(img);
        invalidate();
    }

    public void setUsername(@NonNull String username) {
        renderer.setUsername(username);
        invalidate();
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
