package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.AbsoluteLayout;

import com.arashpayan.gesture.MoveGestureRecognizer;

import io.pijun.george.databinding.ActivityAvatarCropperBinding;

public class AvatarCropperActivity extends AppCompatActivity implements MoveGestureRecognizer.MoveGestureListener, View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private static String ARG_BITMAP = "bitmap";
    private static String ARG_TYPE = "type";
    private static String ARG_URI = "uri";

    private ActivityAvatarCropperBinding mBinding;
    private Bitmap mOriginalImage;
    private MoveGestureRecognizer mMoveRecognizer;
    private ScaleGestureDetector mScaleDetector;
    private PointF mTouchStart;
    private PointF mAvatarStart;
    private boolean mIsScaling = false;
    private float mScalingStartSpan = 0;
    private float mLastScalingValue = 1;

    public static Intent newIntent(@NonNull Context ctx, @NonNull Bitmap img) {
        Intent i = new Intent(ctx, AvatarCropperActivity.class);
        i.putExtra(ARG_TYPE, ARG_BITMAP);
        i.putExtra(ARG_BITMAP, img);
        return i;
    }

    public static Intent newIntent(@NonNull Context ctx, @NonNull Uri uri) {
        Intent i = new Intent(ctx, AvatarCropperActivity.class);
        i.putExtra(ARG_TYPE, ARG_URI);
        i.putExtra(ARG_URI, uri);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("You need to use newIntent to start the activity");
        }
        String type = extras.getString(ARG_TYPE, null);
        if (TextUtils.isEmpty(type)) {
            throw new IllegalArgumentException("Arg type is missing from intent.");
        }
        if (!type.equals(ARG_BITMAP) && !type.equals(ARG_URI)) {
            throw new IllegalArgumentException("Unknown arg type '" + type + "'");
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_avatar_cropper);

        if (type.equals(ARG_BITMAP)) {
            mOriginalImage = extras.getParcelable(ARG_BITMAP);
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    onImageLoaded();
                }
            });
            if (mOriginalImage == null) {
                throw new IllegalArgumentException("You promised me a bitmap");
            }
        } else if (type.equals(ARG_URI)) {
            Uri uri = extras.getParcelable(ARG_URI);
            if (uri == null) {
                throw new IllegalArgumentException("You promised me a uri");
            }
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    try {
                        mOriginalImage = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        App.runOnUiThread(new UiRunnable() {
                            @Override
                            public void run() {
                                onImageLoaded();
                            }
                        });
                    } catch (Exception ex) {
                        L.w("Failed to get bitmap from uri", ex);
                        finish();
                    }
                }
            });
        }

        mBinding.root.setOnTouchListener(this);
        mMoveRecognizer = new MoveGestureRecognizer().setListener(this);
        mScaleDetector = new ScaleGestureDetector(this, this);
    }

    @SuppressWarnings("deprecation")
    @UiThread
    private void onImageLoaded() {
        L.i("onImageLoaded");
        mBinding.avatar.setImageBitmap(mOriginalImage);

        // configure the shades to expose the center of the screen, with width
        // and height equal to 80% of the screen width (portrait)
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
        params.width = mOriginalImage.getWidth();
        params.height = mOriginalImage.getHeight();
        params.x = (int)(width/2.0f - mOriginalImage.getWidth()/2.0f);
        params.y = (int)(height/2.0f - mOriginalImage.getHeight()/2.0f);
        L.i("x: " + params.x + ", y: " + params.y);
        mBinding.avatar.setLayoutParams(params);
    }

    @Override
    public void onMoveGestureBegan(View v, PointF start) {
        mTouchStart = start;
        mAvatarStart = new PointF(mBinding.avatar.getX(), mBinding.avatar.getY());
    }

    @Override
    public void onMoveGestureChanged(View v, PointF current) {
        float dx = current.x - mTouchStart.x;
        float dy = current.y - mTouchStart.y;
        mBinding.avatar.setX(mAvatarStart.x + dx);
        mBinding.avatar.setY(mAvatarStart.y + dy);
    }

    @Override
    public void onMoveGestureEnded(View v, PointF end) {
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float span = detector.getCurrentSpan();
        float spanX = detector.getCurrentSpanX();
        float spanY = detector.getCurrentSpanY();
        float scale = span / mScalingStartSpan;
        scale *= mLastScalingValue;
        if (scale > 2.0f) {
            scale = 2.0f;
        } else if (scale < 0.3f) {
            scale = 0.3f;
        }
//        L.i(String.format("span: %f, x: %f, y: %f", span, spanX, spanY));
//        float newW = (float)mBinding.avatar.getWidth() * scale;
//        float newH = (float)mBinding.avatar.getHeight() * scale;
        mBinding.avatar.animate().scaleX(scale).scaleY(scale).setDuration(0);
        L.i("scale: " + scale);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mScalingStartSpan = detector.getCurrentSpan();
        L.i("onScale Begin: " + mScalingStartSpan);
        mIsScaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        L.i("onScale end");
        float span = detector.getCurrentSpan();
        float scale = span / mScalingStartSpan;
        mLastScalingValue *= scale;
        if (mLastScalingValue > 2.0f) {
            mLastScalingValue = 2.0f;
        } else if (mLastScalingValue < 0.3f) {
            mLastScalingValue = 0.3f;
        }
        mIsScaling = false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        if (!mIsScaling) {
            mMoveRecognizer.onTouch(v, event);
        }
        return true;
    }
}
