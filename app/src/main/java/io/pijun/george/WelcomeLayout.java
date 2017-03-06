package io.pijun.george;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Px;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

public class WelcomeLayout extends ViewGroup {

    public static final int TASK_SPLASH = 0;
    public static final int TASK_SIGN_IN = 1;
    public static final int TASK_REGISTER = 2;

    private boolean mHasInited = false;
    private Rect mScreenSize = null;
    private float mDensity = 0;
    private int mTask = 0;
    private boolean mCloudsMoving = false;
    private View mLogo;
    private View mGlobe;
    private TextView mTitle;
    private TextView mSubtitle;
    private Button mSignIn;
    private Button mRegister;
    private View mSiInputs;
    private View mRegInputs;
    private ImageView mCloud1;
    private ImageView mCloud2;

    public WelcomeLayout(Context context) {
        super(context);
    }

    public WelcomeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WelcomeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressWarnings("unused")
    public WelcomeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init() {
        mHasInited = true;
        mDensity = getResources().getDisplayMetrics().density;
        int count = getChildCount();
        for (int i=0; i<count; i++) {
            View child = getChildAt(i);
            switch (child.getId()) {
                case R.id.logo:
                    mLogo = child;
                    break;
                case R.id.globe:
                    mGlobe = child;
                    break;
                case R.id.screen_title:
                    mTitle = (TextView) child;
                    break;
                case R.id.screen_subtitle:
                    mSubtitle = (TextView) child;
                    break;
                case R.id.sign_in_button:
                    mSignIn = (Button) child;
                    break;
                case R.id.register_button:
                    mRegister = (Button) child;
                    break;
                case R.id.si_inputs:
                    mSiInputs = child;
                    break;
                case R.id.reg_inputs:
                    mRegInputs = child;
                    break;
                case R.id.cloud1:
                    mCloud1 = (ImageView) child;
                    break;
                case R.id.cloud2:
                    mCloud2 = (ImageView) child;
                    break;
            }
        }
    }

    public int pix(int dps) {
        return (int) (dps * mDensity + 0.5f);
    }

    public int dps(int pix) {
        return (int)((pix - 0.5f)/mDensity);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
//        L.i("changed: "+ changed + ", " + l + ", " + t + ", " + r + ", " + b);
        boolean animate = false;
        long duration = 0;
        if (mScreenSize == null) {
            mScreenSize = new Rect(l, t, r, b);
        } else {
            animate = true;
            duration = 150;
        }
        if (!mHasInited) {
            init();
        }

        layoutChildren(r-l, b-t, animate, duration);
    }

    private void layoutChildren(int width, int height, boolean animate, long duration) {
        int dpsHeight = dps(height);
        boolean hideLogo = dpsHeight < 350;

        // CLOUD 1
        int cl1L, cl1T, cl1R, cl1B;
        LayoutParams cl1Params = (LayoutParams) mCloud1.getLayoutParams();
        cl1L = width - mCloud1.getMeasuredWidth() - cl1Params.getMarginEnd();
        cl1T = cl1Params.topMargin;
        cl1R = cl1L + mCloud1.getMeasuredWidth();
        cl1B = cl1T + mCloud1.getMeasuredHeight();
        mCloud1.layout(cl1L, cl1T, cl1R, cl1B);

        // CLOUD 2
        int cl2L, cl2T, cl2R, cl2B;
        LayoutParams cl2Params = (LayoutParams) mCloud2.getLayoutParams();
        cl2L = cl2Params.getMarginStart();
        cl2T = cl2Params.topMargin;
        cl2R = cl2L + mCloud2.getMeasuredWidth();
        cl2B = cl2T + mCloud2.getMeasuredHeight();
        mCloud2.layout(cl2L, cl2T, cl2R, cl2B);

        // LOGO
        LayoutParams logoParams = (LayoutParams) mLogo.getLayoutParams();
        int logoL, logoT, logoR, logoB;
        float logoAlpha;
        int logoW, logoH;
        if (mTask == TASK_SPLASH) {
            logoW = pix(96);
            logoH = pix(113);
//            logoScale = 1;
        } else {
            logoW = pix(79);
            logoH = pix(93);
            //noinspection NumericOverflow
//            logoScale = (float) (79.0/96.0);
        }
        float logoScale;
        if (mLogo.getWidth() != 0) {
            logoScale = (float)logoW / (float)mLogo.getWidth();
        } else {
            logoScale = 1;
        }
        logoL = width/2 - logoW / 2;
        logoT = logoParams.topMargin;
        logoR = logoL + logoW;
        logoB = logoT + logoH;
        if (hideLogo) {
            logoAlpha = 0;
        } else {
            logoAlpha = 1;
        }
        if (animate) {
            final int l=logoL, t=logoT, r=logoR, b=logoB;
            mLogo.setPivotY(0);
            mLogo.animate().
                    alpha(logoAlpha).
                    scaleX(logoScale).
                    scaleY(logoScale).
                    setDuration(duration).
                    withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            mLogo.layout(l, t, r, b);
                            mLogo.setScaleX(1);
                            mLogo.setScaleY(1);
                        }
                    });
        } else {
            mLogo.setAlpha(logoAlpha);
            mLogo.setScaleX(1);
            mLogo.setScaleY(1);
            mLogo.layout(logoL, logoT, logoR, logoB);
        }

        // TITLE
        LayoutParams titleParams = (LayoutParams) mTitle.getLayoutParams();
        int titleL, titleT, titleR, titleB;
        titleL = width/2 - mTitle.getMeasuredWidth()/2;
        if (hideLogo) {
            titleT = titleParams.topMargin;
        } else {
            titleT = logoB + titleParams.topMargin;
        }
        titleR = titleL + mTitle.getMeasuredWidth();
        titleB = titleT + mTitle.getMeasuredHeight();
        if (animate) {
            final int l=titleL, t=titleT, r=titleR, b=titleB;
            mTitle.animate().x(titleL).y(titleT).setDuration(duration).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mTitle.setTranslationX(0);
                    mTitle.setTranslationY(0);
                    mTitle.layout(l, t, r, b);
                }
            });
        } else {
            mTitle.layout(titleL, titleT, titleR, titleB);
        }

        // SUBTITLE
        LayoutParams subtitleParams = (LayoutParams) mSubtitle.getLayoutParams();
        int subtitleL, subtitleT, subtitleR, subtitleB;
        subtitleL = width/2 - mSubtitle.getMeasuredWidth()/2;
        subtitleT = titleB + subtitleParams.topMargin;
        subtitleR = subtitleL + mSubtitle.getMeasuredWidth();
        subtitleB = subtitleT + mSubtitle.getMeasuredHeight();
        if (animate) {
            final int l=subtitleL, t=subtitleT, r=subtitleR, b=subtitleB;
            mSubtitle.animate().x(subtitleL).y(subtitleT).setDuration(duration).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mSubtitle.setTranslationX(0);
                    mSubtitle.setTranslationY(0);
                    mSubtitle.layout(l, t, r, b);
                }
            });
        } else {
            mSubtitle.layout(subtitleL, subtitleT, subtitleR, subtitleB);
        }

        // SIGN IN button
        LayoutParams signInParams = (LayoutParams) mSignIn.getLayoutParams();
        int signInL = 0, signInT = 0, signInR = 0, signInB = 0, signInAlpha=0;
        if (mTask == TASK_SPLASH) {
            signInL = width/2 - mSignIn.getMeasuredWidth()/2;
            signInT = subtitleB + signInParams.topMargin;
            signInR = signInL + mSignIn.getMeasuredWidth();
            signInB = signInT + mSignIn.getMeasuredHeight();
            signInAlpha = 1;
        } else if (mTask == TASK_SIGN_IN) {
            signInL = width - signInParams.getMarginEnd() - mSignIn.getMeasuredWidth();
            signInT = height - mSignIn.getMeasuredHeight() - signInParams.bottomMargin;
            signInR = signInL + mSignIn.getMeasuredWidth();
            signInB = signInT + mSignIn.getMeasuredHeight();
            signInAlpha = 1;
        } else if (mTask == TASK_REGISTER) {
            // use the same values and fade away
            signInL = mSignIn.getLeft();
            signInT = mSignIn.getTop();
            signInR = mSignIn.getRight();
            signInB = mSignIn.getBottom();
            signInAlpha = 0;
        }
        if (animate) {
            final int l=signInL, t=signInT, r=signInR, b=signInB;
            mSignIn.animate().x(signInL).y(signInT).alpha(signInAlpha).setDuration(duration).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mSignIn.setTranslationX(0);
                    mSignIn.setTranslationY(0);
                    mSignIn.layout(l, t, r, b);
                }
            });
        } else {
            mSignIn.layout(signInL, signInT, signInR, signInB);
        }

        // REGISTER button
        LayoutParams regParams = (LayoutParams) mRegister.getLayoutParams();
        int regL=0, regT=0, regR=0, regB=0, regAlpha=0;
        if (mTask == TASK_SPLASH) {
            regL = width/2 - mRegister.getMeasuredWidth()/2;
            regT = signInB + regParams.topMargin;
            regR = regL + mRegister.getMeasuredWidth();
            regB = regT + mRegister.getMeasuredHeight();
            regAlpha = 1;
        } else if (mTask == TASK_SIGN_IN) {
            // use whatever the existing values are and fade away
            regL = mRegister.getLeft();
            regT = mRegister.getTop();
            regR = mRegister.getRight();
            regB = mRegister.getBottom();
            regAlpha = 0;
        } else if (mTask == TASK_REGISTER) {
            regL = width - regParams.getMarginEnd() - mRegister.getMeasuredWidth();
            regT = height - mRegister.getMeasuredHeight() - regParams.bottomMargin;
            regR = regL + mRegister.getMeasuredWidth();
            regB = regT + mRegister.getMeasuredHeight();
            regAlpha = 1;
        }
        if (animate) {
            final int l=regL, t=regT, r=regR, b=regB;
            mRegister.animate().x(regL).y(regT).alpha(regAlpha).setDuration(duration).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mRegister.setTranslationX(0);
                    mRegister.setTranslationY(0);
                    mRegister.layout(l, t, r, b);
                }
            });
        } else {
            mRegister.layout(regL, regT, regR, regB);
        }

        // SIGN IN INPUTS
        LayoutParams siInputsParams = (LayoutParams) mSiInputs.getLayoutParams();
        int siInputsL, siInputsT, siInputsR, siInputsB;
        float siInputsTrX = 0;
        siInputsL = siInputsParams.getMarginStart();
        siInputsT = subtitleB + siInputsParams.topMargin;
        siInputsR = siInputsL + (width - siInputsParams.getMarginEnd() - siInputsParams.getMarginStart());
        siInputsB = siInputsT + mSiInputs.getMeasuredHeight();
        if (mTask != TASK_SIGN_IN) {
            siInputsTrX = width - siInputsL;
        }
        if (animate) {
            final int l=siInputsL, t=siInputsT, r=siInputsR, b=siInputsB;
            final float trX = siInputsTrX;
            mSiInputs.animate().x(siInputsL+siInputsTrX).y(siInputsT).setDuration(duration).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mSiInputs.setTranslationX(trX);
                    mSiInputs.setTranslationY(0);
                    mSiInputs.layout(l, t, r, b);
                }
            });
        } else {
            mSiInputs.layout(siInputsL, siInputsT, siInputsR, siInputsB);
            mSiInputs.setTranslationX(siInputsTrX);
        }

        // REGISTER INPUTS
        LayoutParams regInputsParams = (LayoutParams) mRegInputs.getLayoutParams();
        int regInputsL, regInputsT, regInputsR, regInputsB;
        float regInputsTrX = 0;
        regInputsL = regInputsParams.getMarginStart();
        regInputsT = subtitleB + regInputsParams.topMargin;
        regInputsR = regInputsL + (width - regInputsParams.getMarginEnd() - regInputsParams.getMarginStart());
        regInputsB = regInputsT + mRegInputs.getMeasuredHeight();
        if (mTask != TASK_REGISTER) {
            regInputsTrX = width - regInputsL;
        }
        if (animate) {
            final int l=regInputsL, t=regInputsT, r=regInputsR, b=regInputsB;
            final float trX = regInputsTrX;
            mRegInputs.animate().x(regInputsL+regInputsTrX).y(regInputsT).setDuration(duration).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mRegInputs.setTranslationX(trX);
                    mRegInputs.setTranslationY(0);
                    mRegInputs.layout(l, t, r, b);
                }
            });
        } else {
            mRegInputs.layout(regInputsL, regInputsT, regInputsR, regInputsB);
            mRegInputs.setTranslationX(regInputsTrX);
        }

        // GLOBE
        LayoutParams globeParams = (LayoutParams) mGlobe.getLayoutParams();
        int globeL, globeT, globeB, globeAlpha, globeTrY;
        globeL = 0;
        globeT = height-globeParams.height;
        globeB = height;
        if (mTask == TASK_SPLASH) {
            globeAlpha = 1;
            globeTrY = 0;
        } else {
            globeAlpha = 0;
            globeTrY = globeParams.height;
        }
        mGlobe.layout(globeL, globeT, width, globeB);
        if (animate) {
            mGlobe.animate().alpha(globeAlpha).translationY(globeTrY).setDuration(duration);
        } else {
            mGlobe.setAlpha(globeAlpha);
            mGlobe.setTranslationY(globeTrY);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        for (int i=0; i<count; i++) {
            View v = getChildAt(i);
            measureChild(v, widthMeasureSpec, heightMeasureSpec);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void print(String name, View v) {
        String msg = String.format(Locale.US,
                "%s pos: { l: %d, t: %d, r: %d, b: %d }",
                name,
                v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        if (!isInEditMode()) {
            L.i(msg);
        }
    }

    public void setTask(int task, boolean animate) {
        if (task == mTask) {
            return;
        }

        mTask = task;
        if (mTask == TASK_SPLASH) {
            mSignIn.setEnabled(true);
            mRegister.setEnabled(true);
        } else if (mTask == TASK_SIGN_IN) {
            mSignIn.setEnabled(true);
            mRegister.setEnabled(false);
        } else if (mTask == TASK_REGISTER) {
            mSignIn.setEnabled(false);
            mRegister.setEnabled(true);
        }
        layoutChildren(getWidth(), getHeight(), animate, 300);
    }

    public void setCloudMovementEnabled(boolean enabled) {
        if (mCloudsMoving == enabled) {
            return;
        }

        mCloudsMoving = enabled;
        if (mCloudsMoving) {
            if (mCloud1 != null) {
                scheduleCloudMovement(mCloud1);
            }
            if (mCloud2 != null) {
                scheduleCloudMovement(mCloud2);
            }
        }
    }

    private void scheduleCloudMovement(@NonNull final ImageView cloud) {
        int width = getWidth();
        if (cloud.getX() >= width) {
            cloud.setTranslationX(-cloud.getRight());
        }
        float xBy = getWidth() - cloud.getX();
        cloud.animate().
                translationXBy(xBy).
                setDuration((long)xBy * 75).
                setInterpolator(new LinearInterpolator()).
                withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        scheduleCloudMovement(cloud);
                    }
                });
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(0, 0);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @SuppressWarnings("WeakerAccess")
    public static class LayoutParams extends MarginLayoutParams {

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(@Px int width, @Px int height) {
            super(width, height);
        }

        @SuppressWarnings("unused")
        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }
}
