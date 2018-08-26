package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AbsoluteLayout;

import com.arashpayan.gesture.MoveGestureRecognizer;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import io.pijun.george.databinding.ActivityAvatarCropperBinding;

public class AvatarCropperActivity extends AppCompatActivity implements MoveGestureRecognizer.MoveGestureListener, View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private static String ARG_URI = "uri";

    private ActivityAvatarCropperBinding mBinding;
    private Bitmap mOriginalImage;
    private MoveGestureRecognizer mMoveRecognizer;
    private ScaleGestureDetector mScaleDetector;
    private boolean mIsScaling = false;
    private float mCurrWidth;
    private float mCurrHeight;
    private PointF mLastMovePoint;
    private PointF mImgXY = new PointF();
    private PointF mImgOffset = new PointF();

    @SuppressWarnings("deprecation")
    private void finishLayout() {
        // fix the instructions
        AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) mBinding.instructions.getLayoutParams();
        params.x = (mBinding.root.getWidth() - mBinding.instructions.getWidth())/2;
        L.i("x: " + params.x + ", rootW: " + mBinding.root.getWidth() + ", instW: " + mBinding.instructions.getWidth());
        mBinding.instructions.setLayoutParams(params);

        // and now the 'cancel' and 'done' buttons
        params = (AbsoluteLayout.LayoutParams) mBinding.done.getLayoutParams();
        params.x = mBinding.root.getWidth() - mBinding.done.getWidth();
    }

    public static Intent newIntent(@NonNull Context ctx, @NonNull Uri uri) {
        Intent i = new Intent(ctx, AvatarCropperActivity.class);
        i.putExtra(ARG_URI, uri);
        return i;
    }

    @UiThread
    public void onCancelClicked(View v) {
        finish();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("You need to use newIntent to start the activity");
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_avatar_cropper);

        Uri uri = extras.getParcelable(ARG_URI);
        if (uri == null) {
            throw new IllegalArgumentException("You promised me a uri");
        }
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                mOriginalImage = bitmap;
                onImageLoaded();
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                L.w("Unable to open bitmap for cropping");
                finish();
            }
            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {}
        };
        mBinding.avatar.setTag(target);
        Picasso.with(this).load(uri).into(target);

        mBinding.root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean initialLayoutDone = false;
            @Override
            public void onGlobalLayout() {
                if (!initialLayoutDone) {
                    initialLayoutDone = true;
                    finishLayout();
                }
            }
        });

        mBinding.root.setOnTouchListener(this);
        mMoveRecognizer = new MoveGestureRecognizer().setListener(this);
        mScaleDetector = new ScaleGestureDetector(this, this);
    }

    @UiThread
    public void onDoneClicked(View v) {
        float screenWidth = mBinding.root.getWidth();
        float screenHeight = mBinding.root.getHeight();

        float boxSize = screenWidth * 0.7f;
        float boxLeft = screenWidth * 0.15f;
        float boxTop = (screenHeight - boxSize)/2.0f;
        float imgX = mImgXY.x + mImgOffset.x;
        float imgY = mImgXY.y + mImgOffset.y;
        float imgW = mBinding.avatar.getWidth();
        float imgH = mBinding.avatar.getHeight();
        float cx = ((boxLeft - imgX)/imgW) * mOriginalImage.getWidth();
        float cy = ((boxTop - imgY)/imgH) * mOriginalImage.getHeight();
        float cw = (boxSize/imgW) * mOriginalImage.getWidth();
        float ch = (boxSize/imgH) * mOriginalImage.getHeight();

        mBinding.done.setEnabled(false);
        mBinding.cancel.setEnabled(false);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                L.i("cropping image");
                Bitmap cropped = Bitmap.createBitmap(mOriginalImage, (int) cx, (int) cy, (int) cw, (int) ch, null, false);
                try {
                    boolean success = AvatarManager.setMyAvatar(AvatarCropperActivity.this, cropped);
                    if (success) {
                        App.runOnUiThread(new UiRunnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        });
                        return;
                    }
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            Utils.showStringAlert(AvatarCropperActivity.this, "Save error", "There was a problem saving your profile image. Try again, and if the problem persists, contact support.");
                            mBinding.done.setEnabled(true);
                            mBinding.cancel.setEnabled(true);
                        }
                    });
                } catch (IOException ex) {
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            Utils.showStringAlert(AvatarCropperActivity.this, "Save error", "We couldn't save your profile image. Make sure you have enough free space on your device, then try again.");
                            mBinding.done.setEnabled(true);
                            mBinding.cancel.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @UiThread
    private void onImageLoaded() {
        L.i("onImageLoaded");
        mBinding.avatar.setImageBitmap(mOriginalImage);

        // configure the shades to expose the center of the screen, with width
        // and height equal to 70% of the screen width (portrait)
        float width = mBinding.root.getWidth();
        float height = mBinding.root.getHeight();
        float boxSize = width * 0.7f;
        float yStart = (height - boxSize)/2.0f;

        AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) mBinding.topShade.getLayoutParams();
        params.height = (int)yStart;
        mBinding.topShade.setLayoutParams(params);

        params = (AbsoluteLayout.LayoutParams) mBinding.bottomShade.getLayoutParams();
        params.height = (int)yStart;
        params.y = (int)(yStart + boxSize);
        mBinding.bottomShade.setLayoutParams(params);

        params = (AbsoluteLayout.LayoutParams) mBinding.leftShade.getLayoutParams();
        params.y = (int)yStart;
        params.width = (int)(width * 0.15f);
        params.height = (int)boxSize;
        mBinding.leftShade.setLayoutParams(params);

        params = (AbsoluteLayout.LayoutParams) mBinding.rightShade.getLayoutParams();
        params.x = (int)(width - width * 0.15f);
        params.y = (int)yStart;
        params.width = (int)(width * 0.15f);
        params.height = (int)boxSize;
        mBinding.rightShade.setLayoutParams(params);

        params = (AbsoluteLayout.LayoutParams) mBinding.avatar.getLayoutParams();
        mCurrWidth = mOriginalImage.getWidth();
        mCurrHeight = mOriginalImage.getHeight();
        params.width = (int) mCurrWidth;
        params.height = (int) mCurrHeight;
        mImgXY.x = width/2.0f - mCurrWidth/2.0f;
        mImgXY.y = height/2.0f - mCurrHeight/2.0f;
        params.x = (int) mImgXY.x;
        params.y = (int) mImgXY.y;
        mBinding.avatar.setLayoutParams(params);
    }

    @Override
    public void onMoveGestureBegan(View v, PointF start) {
        mLastMovePoint = start;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onMoveGestureChanged(View v, PointF current) {
        float dx = current.x - mLastMovePoint.x;
        float dy = current.y - mLastMovePoint.y;
        mLastMovePoint = current;
        mImgOffset.x += dx;
        mImgOffset.y += dy;

        AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) mBinding.avatar.getLayoutParams();
        params.x = (int) (mImgXY.x + mImgOffset.x); // += dx;
        params.y = (int) (mImgXY.y + mImgOffset.y); // += dy;
        mBinding.avatar.setLayoutParams(params);
    }

    @Override
    public void onMoveGestureEnded(View v, PointF end) {
        mLastMovePoint = null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scale = detector.getScaleFactor();

        AbsoluteLayout.LayoutParams params = (AbsoluteLayout.LayoutParams) mBinding.avatar.getLayoutParams();
        float newWidth = mCurrWidth * scale;
        float newHeight = mCurrHeight * scale;
        params.width = (int) newWidth;
        params.height = (int) newHeight;
        float fx = detector.getFocusX();
        float fy = detector.getFocusY();
        float dx = fx - mImgXY.x;
        float dy = fy - mImgXY.y;
        dx = dx * scale - dx;
        dy = dy * scale - dy;
        mImgXY.x -= dx;
        mImgXY.y -= dy;
        params.x = (int) mImgXY.x;
        params.y = (int) mImgXY.y;
        mBinding.avatar.setLayoutParams(params);

        mCurrWidth = newWidth;
        mCurrHeight = newHeight;

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        // merge the offst into the image xy, so calculations are easier while we scale
        mImgXY.x = mImgXY.x + mImgOffset.x;
        mImgXY.y = mImgXY.y + mImgOffset.y;
        mImgOffset.x = 0;
        mImgOffset.y = 0;

        mIsScaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mIsScaling = false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        if (!mIsScaling) {
            mMoveRecognizer.onTouch(v, event);
        }
        return true;
    }
}
