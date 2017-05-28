package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Px;
import android.support.annotation.UiThread;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.percent.PercentRelativeLayout;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import io.pijun.george.interpolator.Bezier65Interpolator;
import io.pijun.george.interpolator.BezierLinearInterpolator;
import io.pijun.george.interpolator.LinearBezierInterpolator;

public class WelcomeLayout extends ViewGroup implements View.OnFocusChangeListener {

    public static final int STATE_ANIMATED_INTRO = 0;
    public static final int STATE_LOGO_AND_TITLES = 1;
    public static final int STATE_SPLASH = 2;
    public static final int STATE_REGISTER = 3;
    public static final int STATE_SIGN_IN = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_ANIMATED_INTRO, STATE_LOGO_AND_TITLES, STATE_SPLASH, STATE_REGISTER, STATE_SIGN_IN})
    @interface State {}

    private boolean mHasInited = false;
    private Integer mOriginalHeight = null;
    private float mDensity = 0;
    @State private int mState = STATE_ANIMATED_INTRO;
    private boolean mCloudsMoving = true;
    private View mLogo;
    private View mGlobe;
    private ImageView mTitle;
    private TextView mSubtitle;
    private Button mShowSignIn;
    private Button mShowRegister;
    private ImageView mCloud1;
    private ImageView mCloud2;
    private SignInElements siViews = new SignInElements();
    private RegistrationElements regViews = new RegistrationElements();

    private FocusListener mFocusListener;

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
                    mTitle = (ImageView) child;
                    break;
                case R.id.screen_subtitle:
                    mSubtitle = (TextView) child;
                    break;
                case R.id.show_sign_in_button:
                    mShowSignIn = (Button) child;
                    break;
                case R.id.show_register_button:
                    mShowRegister = (Button) child;
                    break;
                case R.id.cloud1:
                    mCloud1 = (ImageView) child;
                    break;
                case R.id.cloud2:
                    mCloud2 = (ImageView) child;
                    break;
                case R.id.reg_username_container:
                    regViews.usernameC = (TextInputLayout) child;
                    regViews.username = (TextInputEditText) child.findViewById(R.id.reg_username);
                    regViews.username.setOnFocusChangeListener(this);
                    regViews.username.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_user), null, null, null);
                    break;
                case R.id.reg_password_container:
                    regViews.passwordC = (TextInputLayout) child;
                    regViews.password = (TextInputEditText) child.findViewById(R.id.reg_password);
                    regViews.password.setOnFocusChangeListener(this);
                    regViews.password.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_padlock), null, null, null);
                    break;
                case R.id.reg_email_container:
                    regViews.emailC = (TextInputLayout) child;
                    regViews.email = (TextInputEditText) child.findViewById(R.id.reg_email);
                    regViews.email.setOnFocusChangeListener(this);
                    regViews.email.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_email_black_22dp), null, null, null);
                    break;
                case R.id.register_button:
                    regViews.button = (Button) child;
                    break;
                case R.id.si_username_container:
                    siViews.usernameC = (TextInputLayout) child;
                    siViews.username = (TextInputEditText) child.findViewById(R.id.si_username);
                    siViews.username.setOnFocusChangeListener(this);
                    siViews.username.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_user), null, null, null);
                    break;
                case R.id.si_password_container:
                    siViews.passwordC = (TextInputLayout) child;
                    siViews.password = (TextInputEditText) child.findViewById(R.id.si_password);
                    siViews.password.setOnFocusChangeListener(this);
                    siViews.password.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_padlock), null, null, null);
                    break;
                case R.id.sign_in_button:
                    siViews.button = (Button) child;
                    break;
                case R.id.reg_spinner:
                    regViews.spinner = (ProgressBar) child;
                    break;
                case R.id.si_spinner:
                    siViews.spinner = (ProgressBar) child;
                    break;
            }
        }
    }

    @NonNull
    private Drawable getTintedDrawable(@DrawableRes int drawable) {
        Context ctx = getContext();
        Drawable d = ctx.getDrawable(drawable);
        if (d == null) {
            throw new IllegalArgumentException("must use a drawable");
        }
        d = d.mutate();
        ColorStateList csl = ContextCompat.getColorStateList(ctx, R.color.welcome_edittext_drawabletint);
        d.setTintList(csl);
        return d;
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    public int pix(int dps) {
        return (int) (dps * mDensity + 0.5f);
    }

    @Override
    @UiThread
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!mHasInited) {
            init();
        }

        switch (mState) {
            case STATE_ANIMATED_INTRO:
                layoutAnimatedIntro();
                break;
            case STATE_LOGO_AND_TITLES:
                layoutLogoAndTitles(r-l, b-t);
                break;
            case STATE_SPLASH:
                layoutSplash(r-l, b-t);
                break;
            case STATE_REGISTER:
                layoutRegistration(r-l, b-t);
                break;
            case STATE_SIGN_IN:
                layoutSignIn(r-l, b-t);
                break;
            default:
                throw new RuntimeException("Unhandled layout state: " + mState);
        }
    }

    @UiThread
    private void layoutLogoAndTitles(int width, int height) {
        int contentHeight;
        contentHeight = mLogo.getMeasuredHeight();

        LayoutParams titleParams = (LayoutParams) mTitle.getLayoutParams();
        contentHeight += titleParams.topMargin;
        contentHeight += mTitle.getMeasuredHeight();

        LayoutParams subtitleParams = (LayoutParams) mSubtitle.getLayoutParams();
        contentHeight += subtitleParams.topMargin;
        contentHeight += mSubtitle.getMeasuredHeight();

        int logoL, logoT, logoR, logoB;
        logoL = width/2 - mLogo.getMeasuredWidth()/2;
        logoT = height/2 - contentHeight/2;
        logoR = logoL + mLogo.getMeasuredWidth();
        logoB = logoT + mLogo.getMeasuredHeight();
        mLogo.layout(logoL, logoT, logoR, logoB);

        int titleL, titleT, titleR, titleB;
        titleL = width/2 - mTitle.getMeasuredWidth()/2;
        titleT = logoB + titleParams.topMargin;
        titleR = titleL + mTitle.getMeasuredWidth();
        titleB = titleT + mTitle.getMeasuredHeight();
        mTitle.layout(titleL, titleT, titleR, titleB);

        int subtitleL, subtitleT, subtitleR, subtitleB;
        subtitleL = width/2 - mSubtitle.getMeasuredWidth()/2;
        subtitleT = titleB + subtitleParams.topMargin;
        subtitleR = subtitleL + mSubtitle.getMeasuredWidth();
        subtitleB = subtitleT + mSubtitle.getMeasuredHeight();
        mSubtitle.layout(subtitleL, subtitleT, subtitleR, subtitleB);
    }

    @UiThread
    private void layoutAnimatedIntro() {
        // everything should be hidden while the animation is playing
        mLogo.layout(0, 0, 0, 0);
        mTitle.layout(0, 0, 0, 0);
        mSubtitle.layout(0, 0, 0, 0);
        mShowSignIn.layout(0, 0, 0, 0);
        mShowRegister.layout(0, 0, 0, 0);
        mGlobe.layout(0, 0, 0, 0);
        mCloud1.layout(0, 0, 0, 0);
        mCloud2.layout(0, 0, 0, 0);
    }

    @UiThread
    private void layoutSplash(int width, int height) {
        /*
        TOP ELEMENTS
        */
        // LOGO
        int logoL, logoT, logoR, logoB;
        logoL = width/2 - mLogo.getMeasuredWidth()/2;
        logoT = (int)(0.11f * (float)height);
        logoR = logoL + mLogo.getMeasuredWidth();
        logoB = logoT + mLogo.getMeasuredHeight();
        mLogo.layout(logoL, logoT, logoR, logoB);

        // TITLE
        int titleL, titleT, titleR, titleB;
        LayoutParams titleParams = (LayoutParams) mTitle.getLayoutParams();
        titleL = width/2 - mTitle.getMeasuredWidth()/2;
        titleT = logoB + titleParams.topMargin;
        titleR = titleL + mTitle.getMeasuredWidth();
        titleB = titleT + mTitle.getMeasuredHeight();
        mTitle.layout(titleL, titleT, titleR, titleB);

        // SUBTITLE
        int subtitleL, subtitleT, subtitleR, subtitleB;
        LayoutParams subtitleParams = (LayoutParams) mSubtitle.getLayoutParams();
        subtitleL = width/2 - mSubtitle.getMeasuredWidth()/2;
        subtitleT = titleB + subtitleParams.topMargin;
        subtitleR = subtitleL + mSubtitle.getMeasuredWidth();
        subtitleB = subtitleT + mSubtitle.getMeasuredHeight();
        mSubtitle.layout(subtitleL, subtitleT, subtitleR, subtitleB);

        /*
        BOTTOM ELEMENTS
         */
        // GLOBE
        int globeT = height - mGlobe.getMeasuredHeight();
        mGlobe.layout(0, height-mGlobe.getMeasuredHeight(), width, height);

        // 'SHOW SIGN IN' BUTTON
        int ssiL, ssiT, ssiR, ssiB;
        LayoutParams ssiParams = (LayoutParams) mShowSignIn.getLayoutParams();
        ssiL = width/2 - mShowSignIn.getMeasuredWidth()/2;
        ssiT = globeT - ssiParams.bottomMargin - mShowSignIn.getMeasuredHeight();
        ssiR = ssiL + mShowSignIn.getMeasuredWidth();
        ssiB = ssiT + mShowSignIn.getMeasuredHeight();
        mShowSignIn.layout(ssiL, ssiT, ssiR, ssiB);

        int srL, srT, srR, srB;
        srL = width/2 - mShowRegister.getMeasuredWidth()/2;
        srT = ssiT - ssiParams.topMargin - mShowRegister.getMeasuredHeight();
        srR = srL + mShowRegister.getMeasuredWidth();
        srB = srT + mShowRegister.getMeasuredHeight();
        mShowRegister.layout(srL, srT, srR, srB);

        layoutClouds(width);
    }

    @UiThread
    private void layoutRegistration(int width, int height) {
        float headerScale = 80.0f/96.0f;
        int fourPctPix = (int)(0.04f * (float)height);
        int logoL, logoT, logoR, logoB;
        logoL = width/2 - mLogo.getMeasuredWidth()/2;
        logoT = (int)(0.062*(float)height);
        logoR = logoL + mLogo.getMeasuredWidth();
        logoB = logoT + mLogo.getMeasuredHeight();
        mLogo.setPivotX(mLogo.getMeasuredWidth()/2);
        mLogo.setPivotY(0);
        mLogo.setScaleX(headerScale);
        mLogo.setScaleY(headerScale);
        mLogo.layout(logoL, logoT, logoR, logoB);

        int titleL, titleT, titleR, titleB;
        titleL = width/2 - mTitle.getMeasuredWidth()/2;
        titleT = logoT + (int)((float)mLogo.getMeasuredHeight()*headerScale) + fourPctPix;
        titleR = titleL + mTitle.getMeasuredWidth();
        titleB = titleT + mTitle.getMeasuredHeight();
        mTitle.setPivotX(mTitle.getMeasuredWidth()/2);
        mTitle.setPivotY(0);
        mTitle.setScaleX(headerScale);
        mTitle.setScaleY(headerScale);
        mTitle.layout(titleL, titleT, titleR, titleB);

        int subtitleL, subtitleT, subtitleR, subtitleB;
        subtitleL = width/2 - mSubtitle.getMeasuredWidth()/2;
        subtitleT = titleT + (int)((float)mTitle.getMeasuredHeight()*headerScale) + (int)(0.02*(float)height);
        subtitleR = subtitleL + mSubtitle.getMeasuredWidth();
        subtitleB = subtitleT + mSubtitle.getMeasuredHeight();
        mSubtitle.setPivotX(mSubtitle.getMeasuredWidth()/2);
        mSubtitle.setPivotY(0);
        mSubtitle.setScaleX(headerScale);
        mSubtitle.setScaleY(headerScale);
        mSubtitle.layout(subtitleL, subtitleT, subtitleR, subtitleB);

        int usernameL, usernameT, usernameR, usernameB;
        LayoutParams usernameParams = (LayoutParams) regViews.usernameC.getLayoutParams();
        usernameL = usernameParams.getMarginStart();
        usernameT = subtitleT + (int)((float)mSubtitle.getMeasuredHeight()*headerScale) + fourPctPix*2;
        usernameR = width - usernameParams.getMarginEnd();
        usernameB = usernameT + regViews.usernameC.getMeasuredHeight();
        regViews.usernameC.layout(usernameL, usernameT, usernameR, usernameB);

        int passwordL, passwordT, passwordR, passwordB;
        LayoutParams passwordParams = (LayoutParams) regViews.passwordC.getLayoutParams();
        passwordL = passwordParams.getMarginStart();
        passwordT = usernameB + passwordParams.topMargin;
        passwordR = width - passwordParams.getMarginEnd();
        passwordB = passwordT + regViews.passwordC.getMeasuredHeight();
        regViews.passwordC.layout(passwordL, passwordT, passwordR, passwordB);

        int emailL, emailT, emailR, emailB;
        LayoutParams emailParams = (LayoutParams) regViews.emailC.getLayoutParams();
        emailL = emailParams.getMarginStart();
        emailT = passwordB + emailParams.topMargin;
        emailR = width - emailParams.getMarginEnd();
        emailB = emailT + regViews.emailC.getMeasuredHeight();
        regViews.emailC.layout(emailL, emailT, emailR, emailB);

        int regL, regT, regR, regB;
        LayoutParams regParams = (LayoutParams) regViews.button.getLayoutParams();
        regL = width - regParams.getMarginEnd() - regViews.button.getMeasuredWidth();
        regT = height - regParams.bottomMargin - regViews.button.getMeasuredHeight();
        regR = regL + regViews.button.getMeasuredWidth();
        regB = regT + regViews.button.getMeasuredHeight();
        regViews.button.layout(regL, regT, regR, regB);

        int spinnerL, spinnerT, spinnerR, spinnerB;
        spinnerL = regL + (regR - regL)/2 - regViews.spinner.getMeasuredWidth()/2;
        spinnerT = (regB - regT)/2 + regT - regViews.spinner.getMeasuredHeight()/2;
        spinnerR = spinnerL + regViews.spinner.getMeasuredWidth();
        spinnerB = spinnerT + regViews.spinner.getMeasuredHeight();
        regViews.spinner.layout(spinnerL, spinnerT, spinnerR, spinnerB);

        // move the globe out of view
        mGlobe.setTranslationY(mGlobe.getMeasuredHeight());

        // move the buttons to the left of the screen
        mShowRegister.setTranslationX(-mShowRegister.getRight());
        mShowSignIn.setTranslationX(-mShowSignIn.getRight());

        layoutClouds(width);
    }

    @UiThread
    private void layoutSignIn(int width, int height) {
        float headerScale = 80.0f/96.0f;
        int fourPctPix = (int)(0.04f * (float)height);
        int logoL, logoT, logoR, logoB;
        logoL = width/2 - mLogo.getMeasuredWidth()/2;
        logoT = (int)(0.062*(float)height);
        logoR = logoL + mLogo.getMeasuredWidth();
        logoB = logoT + mLogo.getMeasuredHeight();
        mLogo.setPivotX(mLogo.getMeasuredWidth()/2);
        mLogo.setPivotY(0);
        mLogo.setScaleX(headerScale);
        mLogo.setScaleY(headerScale);
        mLogo.layout(logoL, logoT, logoR, logoB);

        int titleL, titleT, titleR, titleB;
        titleL = width/2 - mTitle.getMeasuredWidth()/2;
        titleT = logoT + (int)((float)mLogo.getMeasuredHeight()*headerScale) + fourPctPix;
        titleR = titleL + mTitle.getMeasuredWidth();
        titleB = titleT + mTitle.getMeasuredHeight();
        mTitle.setPivotX(mTitle.getMeasuredWidth()/2);
        mTitle.setPivotY(0);
        mTitle.setScaleX(headerScale);
        mTitle.setScaleY(headerScale);
        mTitle.layout(titleL, titleT, titleR, titleB);

        int subtitleL, subtitleT, subtitleR, subtitleB;
        subtitleL = width/2 - mSubtitle.getMeasuredWidth()/2;
        subtitleT = titleT + (int)((float)mTitle.getMeasuredHeight()*headerScale) + (int)(0.02*(float)height);
        subtitleR = subtitleL + mSubtitle.getMeasuredWidth();
        subtitleB = subtitleT + mSubtitle.getMeasuredHeight();
        mSubtitle.setPivotX(mSubtitle.getMeasuredWidth()/2);
        mSubtitle.setPivotY(0);
        mSubtitle.setScaleX(headerScale);
        mSubtitle.setScaleY(headerScale);
        mSubtitle.layout(subtitleL, subtitleT, subtitleR, subtitleB);

        int usernameL, usernameT, usernameR, usernameB;
        LayoutParams usernameParams = (LayoutParams) siViews.usernameC.getLayoutParams();
        usernameL = usernameParams.getMarginStart();
        usernameT = subtitleT + (int)((float)mSubtitle.getMeasuredHeight()*headerScale) + fourPctPix*2;
        usernameR = width - usernameParams.getMarginEnd();
        usernameB = usernameT + siViews.usernameC.getMeasuredHeight();
        siViews.usernameC.layout(usernameL, usernameT, usernameR, usernameB);

        int passwordL, passwordT, passwordR, passwordB;
        LayoutParams passwordParams = (LayoutParams) siViews.passwordC.getLayoutParams();
        passwordL = passwordParams.getMarginStart();
        passwordT = usernameB + passwordParams.topMargin;
        passwordR = width - passwordParams.getMarginEnd();
        passwordB = passwordT + siViews.passwordC.getMeasuredHeight();
        siViews.passwordC.layout(passwordL, passwordT, passwordR, passwordB);

        int siL, siT, siR, siB;
        LayoutParams siParams = (LayoutParams) siViews.button.getLayoutParams();
        siL = width - siParams.getMarginEnd() - siViews.button.getMeasuredWidth();
        siT = height - siParams.bottomMargin - siViews.button.getMeasuredHeight();
        siR = siL + siViews.button.getMeasuredWidth();
        siB = siT + siViews.button.getMeasuredHeight();
        siViews.button.layout(siL, siT, siR, siB);

        int spinnerL, spinnerT, spinnerR, spinnerB;
        spinnerL = siL + (siR - siL)/2 - siViews.spinner.getMeasuredWidth()/2;
        spinnerT = (siB - siT)/2 + siT - siViews.spinner.getMeasuredHeight()/2;
        spinnerR = spinnerL + siViews.spinner.getMeasuredWidth();
        spinnerB = spinnerT + siViews.spinner.getMeasuredHeight();
        siViews.spinner.layout(spinnerL, spinnerT, spinnerR, spinnerB);

        // move the globe out of view
        mGlobe.setTranslationY(mGlobe.getMeasuredHeight());

        // move the buttons to the left of the screen
        mShowRegister.setTranslationX(-mShowRegister.getRight());
        mShowSignIn.setTranslationX(-mShowSignIn.getRight());

        layoutClouds(width);
    }

    @UiThread
    private void layoutClouds(int width) {
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
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // When the keyboard is shown, we don't want our size to be smaller, so we store the largest
        // size we've encountered and always use that for our layout.
        if (mOriginalHeight == null || MeasureSpec.getSize(heightMeasureSpec) > mOriginalHeight) {
            mOriginalHeight = MeasureSpec.getSize(heightMeasureSpec);
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int hSpec = MeasureSpec.makeMeasureSpec(mOriginalHeight, MeasureSpec.EXACTLY);
        int count = getChildCount();
        for (int i=0; i<count; i++) {
            View v = getChildAt(i);
            WelcomeLayout.LayoutParams lp = (LayoutParams) v.getLayoutParams();
            if (lp.width == LayoutParams.MATCH_PARENT) {
                final int childWidth = Math.max(
                        0,
                        width - getPaddingLeft() - getPaddingRight() - lp.leftMargin - lp.rightMargin);
                final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
                measureChild(v, childWidthMeasureSpec, hSpec);
            } else {
                measureChild(v, wSpec, hSpec);
            }
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mOriginalHeight);
    }

    @SuppressWarnings("unused")
    private void print(String name, View v) {
        String msg = String.format(Locale.US,
                "%s pos: { l: %d, t: %d, r: %d, b: %d }",
                name,
                v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        if (!isInEditMode()) {
            L.i(msg);
        }
    }

    @SuppressLint("SwitchIntDef")
    @UiThread
    public void transitionTo(@State int state) {
        if (mState == state) {
            return;
        }

        switch (state) {
            case STATE_LOGO_AND_TITLES:
                transitionToLogoAndTitles();
                break;
            case STATE_SPLASH:
                transitionToSplash();
                break;
            case STATE_REGISTER:
                transitionToRegistration();
                break;
            case STATE_SIGN_IN:
                transitionToSignIn();
                break;
        }

        mState = state;
    }

    private void transitionToInputsCommon(@State int state) {
        // get the existing top of the header views
        int logoT = mLogo.getTop();
        int titleT = mTitle.getTop();
        int subtitleT = mSubtitle.getTop();

        if (state == STATE_SIGN_IN) {
            layoutSignIn(getWidth(), getHeight());
        } else if (state == STATE_REGISTER) {
            layoutRegistration(getWidth(), getHeight());
        } else {
            throw new RuntimeException("This method is only useful for transitioning to sign in or registration");
        }

        float finalScale = mLogo.getScaleX();

        // animate the logo up
        mLogo.setScaleX(1);
        mLogo.setScaleY(1);
        mLogo.setTranslationY(logoT - mLogo.getTop());
        mLogo.animate().
                translationY(0).
                setDuration(562).
                setStartDelay(0).
                scaleX(finalScale).
                scaleY(finalScale).
                setInterpolator(new Bezier65Interpolator());

        // animate the title up
        mTitle.setScaleX(1);
        mTitle.setScaleY(1);
        mTitle.setTranslationY(titleT - mTitle.getTop());
        mTitle.animate().
                translationY(0).
                setDuration(562).
                setStartDelay(0).
                scaleX(finalScale).
                scaleY(finalScale).
                setInterpolator(new Bezier65Interpolator());

        // animate the subtitle up
        mSubtitle.setScaleX(1);
        mSubtitle.setScaleY(1);
        mSubtitle.setTranslationY(subtitleT - mSubtitle.getTop());
        mSubtitle.animate().
                translationY(0).
                setDuration(562).
                setStartDelay(0).
                scaleX(finalScale).
                scaleY(finalScale).
                setInterpolator(new Bezier65Interpolator());

        // hide the globe
        float globeTrY = mGlobe.getTranslationY();
        mGlobe.setTranslationY(0);
        mGlobe.animate().setStartDelay(0).setDuration(562).translationY(globeTrY).setInterpolator(new LinearBezierInterpolator());

        // animate the buttons off
        float showRegTrX = mShowRegister.getTranslationX();
        mShowRegister.setTranslationX(0);
        mShowRegister.animate().setStartDelay(0).translationX(showRegTrX).setDuration(562).setInterpolator(new BezierLinearInterpolator());

        float showSignInTrx = mShowSignIn.getTranslationX();
        mShowSignIn.setTranslationX(0);
        mShowSignIn.animate().setStartDelay(100).translationX(showSignInTrx).setDuration(750).setInterpolator(new BezierLinearInterpolator());
    }

    @UiThread
    private void transitionToSignIn() {
        transitionToInputsCommon(STATE_SIGN_IN);
        int width = getWidth();
        siViews.usernameC.setTranslationX(width - siViews.usernameC.getLeft());
        siViews.usernameC.animate().translationX(0).setStartDelay(750).setDuration(375).setInterpolator(new LinearBezierInterpolator());

        siViews.passwordC.setTranslationX(width - siViews.passwordC.getLeft());
        siViews.passwordC.animate().translationX(0).setStartDelay(850).setDuration(375).setInterpolator(new LinearBezierInterpolator());

        siViews.button.setTranslationX(width - siViews.button.getLeft());
        siViews.button.animate().translationX(0).setStartDelay(1000).setDuration(375).setInterpolator(new LinearBezierInterpolator());
    }

    @UiThread
    private void transitionToRegistration() {
        transitionToInputsCommon(STATE_REGISTER);
        int width = getWidth();

        // move the input fields off screen, so we can animate them in
        regViews.usernameC.setTranslationX(width - regViews.usernameC.getLeft());
        regViews.usernameC.animate().translationX(0).setStartDelay(750).setDuration(375).setInterpolator(new LinearBezierInterpolator());

        regViews.passwordC.setTranslationX(width - regViews.passwordC.getLeft());
        regViews.passwordC.animate().translationX(0).setStartDelay(850).setDuration(375).setInterpolator(new LinearBezierInterpolator());

        regViews.emailC.setTranslationX(width - regViews.emailC.getLeft());
        regViews.emailC.animate().translationX(0).setStartDelay(950).setDuration(375).setInterpolator(new LinearBezierInterpolator());

        regViews.button.setTranslationX(width - regViews.button.getLeft());
        regViews.button.animate().translationX(0).setStartDelay(1100).setDuration(375).setInterpolator(new LinearBezierInterpolator());
    }

    @SuppressLint("SwitchIntDef")
    @UiThread
    private void transitionToSplash() {
        // The animations differ based on the current screen
        switch (mState) {
            case WelcomeLayout.STATE_LOGO_AND_TITLES:
                transitionToSplashFromLogo();
                break;
            case WelcomeLayout.STATE_REGISTER:
                transitionToSplashFromRegister();
                break;
            case WelcomeLayout.STATE_SIGN_IN:
                transitionToSplashFromSignIn();
                break;
        }
    }

    private void transitionToSplashFromInputsCommon(@State int state) {
        // get the existing tops of the logo, title and subtitle
        int logoT = mLogo.getTop();
        int titleT = mTitle.getTop();
        int subtitleT = mSubtitle.getTop();
        // get the existing scale as well
        float currScale = mLogo.getScaleX();

        layoutSplash(getWidth(), getHeight());

        long initialDelay;
        if (state == STATE_SIGN_IN) {
            initialDelay = 462;
        } else if (state == STATE_REGISTER) {
            initialDelay = 562;
        } else {
            throw new RuntimeException("Must be called with sign in or register");
        }

        /*
        Now that the logo, title and subtitle have been moved to their final
        positions, move each one back to its previous position, and animate
        it into its final positions.
         */
        mLogo.setScaleX(currScale);
        mLogo.setScaleY(currScale);
        mLogo.setTranslationY(logoT - mLogo.getTop());
        mLogo.animate().
                translationY(0).
                setDuration(562).
                setStartDelay(initialDelay).
                scaleX(1).
                scaleY(1).
                setInterpolator(new Bezier65Interpolator());

        mTitle.setScaleX(currScale);
        mTitle.setScaleY(currScale);
        mTitle.setTranslationY(titleT - mTitle.getTop());
        mTitle.animate().
                translationY(0).
                setDuration(562).
                setStartDelay(initialDelay).
                scaleX(1).
                scaleY(1).
                setInterpolator(new Bezier65Interpolator());

        mSubtitle.setScaleX(currScale);
        mSubtitle.setScaleY(currScale);
        mSubtitle.setTranslationY(subtitleT - mSubtitle.getTop());
        mSubtitle.animate().
                translationY(0).
                setDuration(562).
                setStartDelay(initialDelay).
                scaleX(1).
                scaleY(1).
                setInterpolator(new Bezier65Interpolator());

        // bring the hill back up
        mGlobe.animate().
                translationY(0).
                setStartDelay(initialDelay).
                setDuration(562).
                setInterpolator(new LinearBezierInterpolator());

        mShowSignIn.animate().
                translationX(0).
                setStartDelay(initialDelay).
                setInterpolator(new LinearBezierInterpolator()).
                setDuration(562);

        mShowRegister.animate().
                translationX(0).
                setStartDelay(initialDelay+100).
                setInterpolator(new LinearBezierInterpolator()).
                setDuration(562);
    }

    private void transitionToSplashFromSignIn() {
        siViews.usernameC.clearFocus();
        siViews.passwordC.clearFocus();

        transitionToSplashFromInputsCommon(STATE_SIGN_IN);

        int width = getWidth();
        siViews.button.animate().
                translationX(width - siViews.button.getLeft()).
                setStartDelay(0).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
        siViews.passwordC.animate().
                translationX(width - siViews.passwordC.getLeft()).
                setStartDelay(150).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
        siViews.usernameC.animate().
                translationX(width - siViews.usernameC.getLeft()).
                setStartDelay(250).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
    }

    private void transitionToSplashFromRegister() {
        // dismiss the keyboard
        regViews.usernameC.clearFocus();
        regViews.passwordC.clearFocus();
        regViews.emailC.clearFocus();

        transitionToSplashFromInputsCommon(STATE_REGISTER);

        int width = getWidth();
        regViews.button.animate().
                translationX(width - regViews.button.getLeft()).
                setStartDelay(0).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
        regViews.emailC.animate().
                translationX(width - regViews.emailC.getLeft()).
                setStartDelay(150).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
        regViews.passwordC.animate().
                translationX(width - regViews.passwordC.getLeft()).
                setStartDelay(250).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
        regViews.usernameC.animate().
                translationX(width - regViews.usernameC.getLeft()).
                setStartDelay(350).
                setDuration(375).
                setInterpolator(new BezierLinearInterpolator());
    }

    private void transitionToSplashFromLogo() {
        int height = getHeight();

        int logoTStart = mLogo.getTop();
        layoutSplash(getWidth(), height);

        int group1TrY = logoTStart - mLogo.getTop();
        mLogo.setTranslationY(group1TrY);
        mTitle.setTranslationY(group1TrY);
        mSubtitle.setTranslationY(group1TrY);
        mLogo.animate().translationY(0).setDuration(1125).setInterpolator(new Bezier65Interpolator());
        mTitle.animate().translationY(0).setDuration(1125).setInterpolator(new Bezier65Interpolator());
        mSubtitle.animate().translationY(0).setDuration(1125).setInterpolator(new Bezier65Interpolator());

        mShowRegister.setTranslationY(height - mShowRegister.getTop());
        mShowRegister.animate().translationY(0).setDuration(562).setStartDelay(675).setInterpolator(new LinearBezierInterpolator());
        mShowSignIn.setTranslationY(height - mShowSignIn.getTop());
        mShowSignIn.animate().translationY(0).setDuration(562).setStartDelay(775).setInterpolator(new LinearBezierInterpolator());

        mGlobe.setTranslationY(height - mGlobe.getTop());
        mGlobe.animate().translationY(0).setDuration(217).setStartDelay(775).setInterpolator(new LinearBezierInterpolator());

        int cloudsTrY = height - mCloud1.getTop();
        mCloud1.setTranslationY(cloudsTrY);
        mCloud1.animate().translationY(0).translationX(100).setDuration(690).setStartDelay(375).setInterpolator(new LinearBezierInterpolator());
        mCloud2.setTranslationY(cloudsTrY);
        mCloud2.animate().translationY(0).translationX(100).setDuration(690).setStartDelay(375).setInterpolator(new LinearBezierInterpolator());
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                scheduleCloudMovement(mCloud1, 56);
                scheduleCloudMovement(mCloud2, 40);
            }
        }, 580);
    }

    @UiThread
    private void transitionToLogoAndTitles() {
        layoutLogoAndTitles(getWidth(), getHeight());

        mSubtitle.setAlpha(0);
        mSubtitle.animate().alpha(1).setDuration(375).setInterpolator(new LinearInterpolator());

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                transitionToSplash();
            }
        }, 1000);
    }

    @State
    public int getState() {
        return mState;
    }

    @UiThread
    public void setCloudMovementEnabled(boolean enabled) {
        if (mCloudsMoving == enabled) {
            return;
        }

        mCloudsMoving = enabled;
        if (mCloudsMoving) {
            if (mCloud1 != null) {
                scheduleCloudMovement(mCloud1, 56);
            }
            if (mCloud2 != null) {
                scheduleCloudMovement(mCloud2, 40);
            }
        } else {
            mCloud1.animate().cancel();
            mCloud2.animate().cancel();
        }
    }

    private void scheduleCloudMovement(@NonNull final ImageView cloud, final int speed) {
        if (!mCloudsMoving) {
            return;
        }
        int width = getWidth();
        if (cloud.getX() >= width) {
            cloud.setTranslationX(-cloud.getRight());
        }
        float xBy = width - cloud.getX();
        // speed is in DIPs per second
        int speedPx = pix(speed);
        cloud.animate().
                translationXBy(xBy).
                setDuration((long)xBy * 1000 / speedPx).
                setInterpolator(new LinearInterpolator()).
                withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        scheduleCloudMovement(cloud, speed);
                    }
                }).start();
    }

    @UiThread
    void setRegistrationSpinnerVisible(boolean visible) {
        if (visible) {
            regViews.spinner.setAlpha(0);
            regViews.spinner.animate().alpha(1).setDuration(200);
            regViews.spinner.setVisibility(View.VISIBLE);
            regViews.button.animate().alpha(0).withEndAction(new UiRunnable() {
                @Override
                public void run() {
                    regViews.button.setVisibility(View.INVISIBLE);
                }
            }).setDuration(200).setInterpolator(new LinearInterpolator()).setStartDelay(0);
        } else {
            regViews.spinner.animate().alpha(0).withEndAction(new UiRunnable() {
                @Override
                public void run() {
                    regViews.spinner.setVisibility(View.GONE);
                }
            }).setDuration(200).setInterpolator(new LinearInterpolator());
            regViews.button.setVisibility(View.VISIBLE);
            regViews.button.animate().setDuration(200).alpha(1).setInterpolator(new LinearInterpolator()).setStartDelay(0);
        }
    }

    @UiThread
    void setSignInSpinnerVisible(boolean visible) {
        if (visible) {
            siViews.spinner.setAlpha(0);
            siViews.spinner.animate().alpha(1).setDuration(200);
            siViews.spinner.setVisibility(View.VISIBLE);
            siViews.button.animate().alpha(0).withEndAction(new UiRunnable() {
                @Override
                public void run() {
                    siViews.button.setVisibility(View.INVISIBLE);
                }
            }).setDuration(200).setInterpolator(new LinearInterpolator()).setStartDelay(0);
        } else {
            siViews.spinner.animate().alpha(0).withEndAction(new UiRunnable() {
                @Override
                public void run() {
                    siViews.spinner.setVisibility(View.GONE);
                }
            }).setDuration(200).setInterpolator(new LinearInterpolator());
            siViews.button.setVisibility(View.VISIBLE);
            siViews.button.animate().setDuration(200).alpha(1).setInterpolator(new LinearInterpolator()).setStartDelay(0);
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (mFocusListener != null && hasFocus) {
            mFocusListener.onWelcomeLayoutFocused(v);
        }

        if (mState == STATE_REGISTER) {
            if (!hasFocus) {
                regViews.usernameC.animate().alpha(1).setStartDelay(0).setDuration(500).start();
                regViews.passwordC.animate().alpha(1).setStartDelay(0).setDuration(500).start();
                regViews.emailC.animate().alpha(1).setStartDelay(0).setDuration(500).start();
            } else {
                if (v == regViews.username) {
                    regViews.passwordC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                    regViews.emailC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();

                } else if (v == regViews.password) {
                    regViews.usernameC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                    regViews.emailC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                } else if (v == regViews.email) {
                    regViews.usernameC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                    regViews.passwordC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                }
            }
        } else if (mState == STATE_SIGN_IN) {
            if (!hasFocus) {
                siViews.usernameC.animate().alpha(1).setStartDelay(0).setDuration(500).start();
                siViews.passwordC.animate().alpha(1).setStartDelay(0).setDuration(500).start();
            } else {
                if (v == siViews.username) {
                    siViews.passwordC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                } else if (v == siViews.password) {
                    siViews.usernameC.animate().alpha(0.5f).setStartDelay(0).setDuration(500).start();
                }
            }
        }
    }

    void setFocusListener(FocusListener l) {
        mFocusListener = l;
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
    public static class LayoutParams extends PercentRelativeLayout.LayoutParams {

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(@Px int width, @Px int height) {
            super(width, height);
        }

        @SuppressWarnings("unused")
        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    private static class SignInElements {
        TextInputLayout usernameC;
        TextInputEditText username;
        TextInputLayout passwordC;
        TextInputEditText password;
        Button button;
        ProgressBar spinner;
    }

    private static class RegistrationElements {
        TextInputLayout usernameC;
        TextInputEditText username;
        TextInputLayout passwordC;
        TextInputEditText password;
        TextInputLayout emailC;
        TextInputEditText email;
        Button button;
        ProgressBar spinner;
    }

    interface FocusListener {
        void onWelcomeLayoutFocused(View view);
    }
}
