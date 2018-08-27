package io.pijun.george;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import io.pijun.george.databinding.ActivityWelcomeBinding;

public class WelcomeViewHolder implements ViewTreeObserver.OnGlobalLayoutListener {

    // TODO disable 'back' during animations
    enum State {
        Main,
        Registration,
        Login
    }

    // Default is true, because the clouds should start moving right away
    private boolean areCloudsMoving = true;
    private ActivityWelcomeBinding binding;
    private State state = State.Main;
    private float displayDensity = 0;
    @NonNull private final Listener listener;

    WelcomeViewHolder(@NonNull ActivityWelcomeBinding binding, @NonNull Listener listener) {
        this.binding = binding;
        this.listener = listener;

        // hide everything until we're notified that the initial layout has been completed
        binding.logo.setVisibility(View.INVISIBLE);
        binding.wordmark.setVisibility(View.INVISIBLE);
        binding.motto.setVisibility(View.INVISIBLE);

        binding.cloud1.setVisibility(View.INVISIBLE);
        binding.cloud2.setVisibility(View.INVISIBLE);

        binding.showRegisterButton.setVisibility(View.INVISIBLE);
        binding.showSignInButton.setVisibility(View.INVISIBLE);

        binding.siUsernameContainer.setVisibility(View.INVISIBLE);
        binding.siPasswordContainer.setVisibility(View.INVISIBLE);
        binding.signInButton.setVisibility(View.INVISIBLE);

        binding.regUsernameContainer.setVisibility(View.INVISIBLE);
        binding.regPasswordContainer.setVisibility(View.INVISIBLE);
        binding.regEmailContainer.setVisibility(View.INVISIBLE);
        binding.registerButton.setVisibility(View.INVISIBLE);

        binding.root.getViewTreeObserver().addOnGlobalLayoutListener(this);

        applyTextFieldDrawables();
        applyTextFieldActionListeners();
    }

    private void applyTextFieldActionListeners() {
        binding.siUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.siPassword.requestFocus();
                return true;
            }

            return false;
        });

        binding.siPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                listener.onSignInAction();
                return true;
            }
            return false;
        });

        binding.regUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.regPassword.requestFocus();
                return true;
            }
            return false;
        });

        binding.regPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.regEmail.requestFocus();
                return true;
            }
            return false;
        });

        binding.regEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                listener.onRegisterAction();
                return true;
            }
            return false;
        });
    }

    private void applyTextFieldDrawables() {
        binding.regUsername.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_sharp_person_24px), null, null, null);
        binding.regPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_sharp_lock_24px), null, null, null);
        binding.regEmail.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_sharp_email_24px), null, null, null);
        binding.siUsername.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_sharp_person_24px), null, null, null);
        binding.siPassword.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_sharp_lock_24px), null, null, null);
    }

    void clearFocus() {
        binding.regUsername.clearFocus();
        binding.regPassword.clearFocus();
        binding.regEmail.clearFocus();

        binding.siUsername.clearFocus();
        binding.siPassword.clearFocus();

        InputMethodManager mgr = (InputMethodManager) binding.root.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (mgr != null) {
            mgr.hideSoftInputFromWindow(binding.root.getWindowToken(), 0);
        }
    }

    State getState() {
        return state;
    }

    @NonNull
    private Drawable getTintedDrawable(@DrawableRes int drawable) {
        Context ctx = binding.getRoot().getContext();
        Drawable d = ctx.getDrawable(drawable);
        if (d == null) {
            throw new IllegalArgumentException("must use a drawable");
        }
        d = d.mutate();
        ColorStateList csl = ContextCompat.getColorStateList(ctx, R.color.welcome_edittext_drawabletint);
        d.setTintList(csl);
        return d;
    }

    //region OnGlobalLayoutListener to listen for the initial layout

    @Override
    public void onGlobalLayout() {
        // we don't want any further layout events
        binding.root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        displayDensity = binding.root.getResources().getDisplayMetrics().density;
        binding.logo.setPivotX(binding.logo.getMeasuredWidth()/2.0f);
        binding.logo.setPivotY(binding.logo.getMeasuredHeight());
        binding.wordmark.setPivotX(binding.wordmark.getMeasuredWidth()/2.0f);
        binding.wordmark.setPivotY(binding.wordmark.getMeasuredHeight());
        binding.motto.setPivotX(binding.motto.getMeasuredWidth()/2.0f);
        binding.motto.setPivotY(binding.motto.getMeasuredHeight());

        transitionToMainFromLaunch();
    }

    //endregion

    //region state transition methods

    private void finishTransitionToMain() {
        // move the clouds to their animation start position
        binding.cloud1.setTranslationY(binding.root.getHeight());
        binding.cloud2.setTranslationY(binding.root.getHeight());
        binding.cloud1.setVisibility(View.VISIBLE);
        binding.cloud2.setVisibility(View.VISIBLE);

        // start animating the branding items
        binding.logo.animate().setStartDelay(500).setDuration(1000).translationY(0);
        binding.wordmark.animate().setStartDelay(500).setDuration(1000).translationY(0);
        binding.motto.animate().setStartDelay(500).setDuration(1000).translationY(0);

        // start animating the clouds into view
        binding.cloud1.animate().setStartDelay(500).setDuration(1000).translationY(0);
        binding.cloud2.animate().setStartDelay(500).setDuration(1000).translationY(0);

        // start moving the clouds horizontally halfway into their appearance animation
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                scheduleCloudMovement(binding.cloud1, 56);
                scheduleCloudMovement(binding.cloud2, 40);
            }
        }, 1000);

        // now let's set up the 'show' buttons
        binding.showRegisterButton.setTranslationY(binding.root.getHeight());
        binding.showSignInButton.setTranslationY(binding.root.getHeight());
        binding.showRegisterButton.setVisibility(View.VISIBLE);
        binding.showSignInButton.setVisibility(View.VISIBLE);
        binding.showRegisterButton.animate().setStartDelay(500).setDuration(1000).translationY(0);
        binding.showSignInButton.animate().setStartDelay(600).setDuration(1000).translationY(0);
    }

    void transitionToLogin() {
        if (state != State.Main) {
            return;
        }

        state = State.Login;

        // calculate the offset for the start position of the login fields and button
        int xOffset = binding.root.getWidth() - binding.siUsernameContainer.getLeft();
        // move them to that start position
        binding.siUsernameContainer.setTranslationX(xOffset);
        binding.siPasswordContainer.setTranslationX(xOffset);
        binding.signInButton.setTranslationX(binding.root.getWidth() - binding.signInButton.getLeft());
        // update their visibility
        binding.siUsernameContainer.setVisibility(View.VISIBLE);
        binding.siPasswordContainer.setVisibility(View.VISIBLE);
        binding.signInButton.setVisibility(View.VISIBLE);

        // move the 'show' buttons and globe first
        binding.showRegisterButton.animate().setStartDelay(0).setDuration(500).translationX(-binding.showRegisterButton.getRight());
        binding.showSignInButton.animate().setStartDelay(100).setDuration(500).translationX(-binding.showSignInButton.getRight());

        // bring in the login fields
        binding.siUsernameContainer.animate().setStartDelay(100).setDuration(500).translationX(0);
        binding.siPasswordContainer.animate().setStartDelay(200).setDuration(500).translationX(0);
        binding.signInButton.animate().setStartDelay(300).setStartDelay(500).translationX(0);
    }

    void transitionToMain() {
        if (state == null) {
            return;
        }

        switch (state) {
            case Registration:
                transitionToMainFromRegistration();
                break;
            case Login:
                transitionToMainFromLogin();
                break;
            case Main:
                break;
        }
    }

    private void transitionToMainFromLaunch() {
        // figure out how tall the logo and wordmark are, so we can place them at the center of the screen
        // NOTE: We're purposely not including the height of the motto in this calculation
        int contentHeight = binding.wordmark.getBottom() - binding.logo.getTop();

        // figure out the offset we'll need from their default position
        int startY = (binding.root.getHeight() - contentHeight)/2;
        int offsetY = startY - binding.logo.getTop();

        binding.logo.setTranslationY(offsetY);
        binding.logo.setAlpha(0f);
        binding.wordmark.setTranslationY(offsetY);
        binding.wordmark.setAlpha(0f);
        binding.motto.setTranslationY(offsetY);
        binding.motto.setAlpha(0f);

        binding.logo.setVisibility(View.VISIBLE);
        binding.wordmark.setVisibility(View.VISIBLE);
        binding.motto.setVisibility(View.VISIBLE);

        binding.logo.animate().setStartDelay(200).setDuration(350).alpha(1);
        binding.wordmark.animate().setStartDelay(200).setDuration(350).alpha(1);
        binding.motto.animate().setStartDelay(1500).setDuration(350).alpha(1).withEndAction(this::finishTransitionToMain);
    }

    private void transitionToMainFromLogin() {
        if (state != State.Login) {
            throw new RuntimeException("You have to be in the Login state to use this transition");
        }
        state = State.Main;

        // figure out the login fields
        int xOffset = binding.root.getWidth() - binding.siUsernameContainer.getLeft();

        // take out the login fields
        binding.signInButton.animate().setStartDelay(0).setDuration(500).translationX(binding.root.getWidth() - binding.signInButton.getLeft());
        binding.siPasswordContainer.animate().setStartDelay(100).setDuration(500).translationX(xOffset);
        binding.siUsernameContainer.animate().setStartDelay(200).setDuration(500).translationX(xOffset);

        // bring back the 'show' buttons and globe
        binding.showRegisterButton.animate().setStartDelay(200).setDuration(500).translationX(0);
        binding.showSignInButton.animate().setStartDelay(300).setDuration(500).translationX(0).withEndAction(new UiRunnable() {
            @Override
            public void run() {
                binding.siUsername.setText(null);
                binding.siPassword.setText(null);
                binding.siUsername.clearFocus();
                binding.siPassword.clearFocus();
            }
        });
    }

    private void transitionToMainFromRegistration() {
        if (state != State.Registration) {
            throw new RuntimeException("You have to be in the registration state to use this transition");
        }

        state = State.Main;

        // figure out the offset for the registration fields
        int xOffset = binding.root.getWidth() - binding.regUsernameContainer.getLeft();

        // take out the registration fields
        binding.registerButton.animate().setStartDelay(0).setDuration(500).translationX(binding.root.getWidth() - binding.registerButton.getLeft());
        binding.regEmailContainer.animate().setStartDelay(100).setDuration(500).translationX(xOffset);
        binding.regPasswordContainer.animate().setStartDelay(200).setDuration(500).translationX(xOffset);
        binding.regUsernameContainer.animate().setStartDelay(300).setDuration(500).translationX(xOffset);

        // animate the 'show' buttons and globe back
        binding.showRegisterButton.animate().setStartDelay(300).setDuration(500).translationX(0);
        binding.showSignInButton.animate().setStartDelay(400).setDuration(500).translationX(0).withEndAction(new UiRunnable() {
            @Override
            public void run() {
                binding.regUsername.setText(null);
                binding.regPassword.setText(null);
                binding.regEmail.setText(null);
            }
        });
    }

    void transitionToRegistration() {
        if (state != State.Main) {
            return;
        }

        state = State.Registration;

        // set up the start position of the registration fields and button
        int xOffset = binding.root.getWidth() - binding.regUsernameContainer.getLeft();
        // We start them from just right of the screen
        binding.regUsernameContainer.setTranslationX(xOffset);
        binding.regPasswordContainer.setTranslationX(xOffset);
        binding.regEmailContainer.setTranslationX(xOffset);
        binding.registerButton.setTranslationX(binding.root.getWidth() - binding.registerButton.getLeft());
        // update their visibility
        binding.regUsernameContainer.setVisibility(View.VISIBLE);
        binding.regPasswordContainer.setVisibility(View.VISIBLE);
        binding.regEmailContainer.setVisibility(View.VISIBLE);
        binding.registerButton.setVisibility(View.VISIBLE);

        // move the 'show' buttons and the globe first
        binding.showRegisterButton.animate().setStartDelay(0).setDuration(500).translationX(-binding.showRegisterButton.getRight());
        binding.showSignInButton.animate().setStartDelay(100).setDuration(500).translationX(-binding.showSignInButton.getRight());

        // bring in the registration fields
        binding.regUsernameContainer.animate().setStartDelay(100).setDuration(500).translationX(0);
        binding.regPasswordContainer.animate().setStartDelay(200).setDuration(500).translationX(0);
        binding.regEmailContainer.animate().setStartDelay(300).setDuration(500).translationX(0);
        binding.registerButton.animate().setStartDelay(400).setDuration(500).translationX(0);
    }

    //endregion

    //region cloud movement

    private void scheduleCloudMovement(@NonNull final ImageView cloud, final int speed) {
        if (!areCloudsMoving) {
            return;
        }
        int width = binding.root.getWidth();
        if (cloud.getX() >= width) {
            cloud.setTranslationX(-cloud.getRight());
        }
        float xBy = width - cloud.getX();
        // speed is in DIPs per second, so let's convert it to pixels/s
        int speedPx = (int) (speed * displayDensity + 0.5f);
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

    void setCloudMovementEnabled(boolean enabled) {
        if (areCloudsMoving == enabled) {
            return;
        }

        areCloudsMoving = enabled;
        if (areCloudsMoving) {
            if (binding.cloud1 != null) {
                scheduleCloudMovement(binding.cloud1, 56);
            }
            if (binding.cloud2 != null) {
                scheduleCloudMovement(binding.cloud2, 40);
            }
        } else {
            binding.cloud1.animate().cancel();
            binding.cloud2.animate().cancel();
        }
    }

    //endregion

    interface Listener {
        @UiThread void onSignInAction();
        @UiThread void onRegisterAction();
    }
}
