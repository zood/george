package io.pijun.george;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import xyz.zood.george.databinding.ActivityWelcomeBinding;

public class WelcomeViewHolder implements ViewTreeObserver.OnGlobalLayoutListener {

    // TODO disable 'back' during animations
    enum State {
        Main,
        Registration,
        Login
    }

    private final ActivityWelcomeBinding binding;
    private State state = State.Main;
    @NonNull private final Listener listener;

    WelcomeViewHolder(@NonNull ActivityWelcomeBinding binding, @NonNull Listener listener) {
        this.binding = binding;
        this.listener = listener;

        binding.root.getViewTreeObserver().addOnGlobalLayoutListener(this);

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

    //region OnGlobalLayoutListener to listen for the initial layout

    @Override
    public void onGlobalLayout() {
        // we don't want any further layout events
        binding.root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

        transitionToMainFromLaunch();
    }

    //endregion

    //region state transition methods

    private void hideMain(long dur) {
        // move the 'main' stuff out first
        binding.welcomeLabel.animate().setStartDelay(0).setDuration(dur).translationX(-binding.welcomeLabel.getRight());
        binding.mainInstructions.animate().setStartDelay(0).setDuration(dur).translationX(-binding.mainInstructions.getRight());
        binding.showRegisterButton.animate().setStartDelay(0).setDuration(dur).translationX(-binding.showRegisterButton.getRight());
        binding.showSignInButton.animate().setStartDelay(0).setDuration(dur).translationX(-binding.showSignInButton.getRight());

        // also move the wordmark and 'developed by' items
        binding.developedByZood.animate().setStartDelay(0).setDuration(dur).translationY(1000);
        binding.wordmark.animate().setStartDelay(0).setDuration(dur).translationY(1000);
    }

    void transitionToLogin() {
        if (state != State.Main) {
            return;
        }
        state = State.Login;
        long dur = 500;

        // calculate the offset for the start position of the login fields and button
        int xOffset = binding.root.getWidth() - binding.siUsernameContainer.getLeft();
        // move them to that start position
        binding.siTitle.setTranslationX(xOffset);
        binding.siSubtitle.setTranslationX(xOffset);
        binding.siUsernameContainer.setTranslationX(xOffset);
        binding.siPasswordContainer.setTranslationX(xOffset);
        binding.signInButton.setTranslationX(binding.root.getWidth() - binding.signInButton.getLeft());
        // update their visibility
        binding.siTitle.setVisibility(View.VISIBLE);
        binding.siSubtitle.setVisibility(View.VISIBLE);
        binding.siUsernameContainer.setVisibility(View.VISIBLE);
        binding.siPasswordContainer.setVisibility(View.VISIBLE);
        binding.signInButton.setVisibility(View.VISIBLE);

        hideMain(dur);

        // move the logo to match the placeholder logo's position
        int logoOffset = binding.siLogoPlaceholder.getTop() - binding.logo.getTop();
        binding.logo.animate().setStartDelay(0).setDuration(dur).translationY(logoOffset);

        // bring in the login fields
        binding.siTitle.animate().setStartDelay(100).setDuration(dur).translationX(0);
        binding.siSubtitle.animate().setStartDelay(200).setDuration(dur).translationX(0);
        binding.siUsernameContainer.animate().setStartDelay(300).setDuration(dur).translationX(0);
        binding.siPasswordContainer.animate().setStartDelay(400).setDuration(dur).translationX(0);
        binding.signInButton.animate().setStartDelay(500).setStartDelay(dur).translationX(0);
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
        L.i("main from launch");
        long dur = 500;
        long delay = 0;

        binding.logo.setAlpha(0f);
        binding.welcomeLabel.setAlpha(0f);
        binding.mainInstructions.setAlpha(0f);
        binding.wordmark.setAlpha(0f);
        binding.developedByZood.setAlpha(0f);
        binding.showRegisterButton.setAlpha(0f);
        binding.showSignInButton.setAlpha(0f);

        binding.logo.setVisibility(View.VISIBLE);
        binding.welcomeLabel.setVisibility(View.VISIBLE);
        binding.mainInstructions.setVisibility(View.VISIBLE);
        binding.showRegisterButton.setVisibility(View.VISIBLE);
        binding.showSignInButton.setVisibility(View.VISIBLE);
        binding.wordmark.setVisibility(View.VISIBLE);
        binding.developedByZood.setVisibility(View.VISIBLE);

        binding.logo.animate().alpha(1).setDuration(dur).setStartDelay(delay);
        binding.welcomeLabel.animate().alpha(1).setDuration(dur).setStartDelay(delay);
        binding.mainInstructions.animate().alpha(1).setDuration(dur).setStartDelay(delay);
        binding.wordmark.animate().alpha(1).setDuration(dur).setStartDelay(delay);
        binding.developedByZood.animate().alpha(1).setDuration(dur).setStartDelay(delay);
        binding.showRegisterButton.animate().alpha(1).setDuration(dur).setStartDelay(delay);
        binding.showSignInButton.animate().alpha(1).setDuration(dur).setStartDelay(delay);
    }

    private void transitionToMainFromLogin() {
        if (state != State.Login) {
            throw new RuntimeException("You have to be in the Login state to use this transition");
        }
        state = State.Main;

        long dur = 500;

        // figure out the offset for login views
        int xOffset = binding.root.getWidth() - binding.siUsernameContainer.getLeft();

        // take out the login views
        binding.signInButton.animate().setStartDelay(0).setDuration(dur).translationX(binding.root.getWidth() - binding.signInButton.getLeft());
        binding.siPasswordContainer.animate().setStartDelay(100).setDuration(dur).translationX(xOffset);
        binding.siUsernameContainer.animate().setStartDelay(200).setDuration(dur).translationX(xOffset);
        binding.siSubtitle.animate().setStartDelay(300).setDuration(dur).translationX(xOffset);
        binding.siTitle.animate().setStartDelay(400).setDuration(dur).translationX(xOffset);

        // bring the logo down
        binding.logo.animate().setStartDelay(500).setDuration(dur).translationY(0);

        // bring back the main views
        binding.wordmark.animate().setStartDelay(500).setDuration(dur).translationY(0);
        binding.developedByZood.animate().setStartDelay(500).setDuration(dur).translationY(0);

        binding.welcomeLabel.animate().setStartDelay(500).setDuration(dur).translationX(0);
        binding.mainInstructions.animate().setStartDelay(500).setDuration(dur).translationX(0);
        // the buttons should start coming in at the same time
        binding.showRegisterButton.animate().setStartDelay(500).setDuration(dur).translationX(0);
        binding.showSignInButton.animate().setStartDelay(500).setDuration(dur).translationX(0).withEndAction(new UiRunnable() {
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

        long dur = 350;

        // figure out the offset for the registration fields exit
        int xOffset = binding.root.getWidth() - binding.regUsernameContainer.getLeft();

        // take out the registration views
        binding.registerButton.animate().setStartDelay(0).setDuration(dur).translationX(binding.root.getWidth() - binding.registerButton.getLeft());
        binding.regEmailContainer.animate().setStartDelay(100).setDuration(dur).translationX(xOffset);
        binding.regPasswordContainer.animate().setStartDelay(200).setDuration(dur).translationX(xOffset);
        binding.regUsernameContainer.animate().setStartDelay(300).setDuration(dur).translationX(xOffset);
        binding.registerSubtitle.animate().setStartDelay(400).setDuration(dur).translationX(xOffset);
        binding.registerTitle.animate().setStartDelay(500).setDuration(dur).translationX(xOffset);

        // bring the logo down
        binding.logo.animate().setStartDelay(500).setDuration(dur).translationY(0);

        // bring back the main views
        binding.wordmark.animate().setStartDelay(500).setDuration(dur).translationY(0);
        binding.developedByZood.animate().setStartDelay(500).setDuration(dur).translationY(0);

        binding.welcomeLabel.animate().setStartDelay(500).setDuration(dur).translationX(0);
        binding.mainInstructions.animate().setStartDelay(500).setDuration(dur).translationX(0);
        // the buttons should start coming in at the same time
        binding.showRegisterButton.animate().setStartDelay(500).setDuration(dur).translationX(0);
        binding.showSignInButton.animate().setStartDelay(500).setDuration(dur).translationX(0).withEndAction(new UiRunnable() {
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

        long dur = 500;

        // set up the start position of the registration fields and button
        int xOffset = binding.root.getWidth() - binding.regUsernameContainer.getLeft();
        // We start them from just right of the screen
        binding.registerTitle.setTranslationX(xOffset);
        binding.registerSubtitle.setTranslationX(xOffset);
        binding.regUsernameContainer.setTranslationX(xOffset);
        binding.regPasswordContainer.setTranslationX(xOffset);
        binding.regEmailContainer.setTranslationX(xOffset);
        binding.registerButton.setTranslationX(binding.root.getWidth() - binding.registerButton.getLeft());
        // update their visibility
        binding.registerTitle.setVisibility(View.VISIBLE);
        binding.registerSubtitle.setVisibility(View.VISIBLE);
        binding.regUsernameContainer.setVisibility(View.VISIBLE);
        binding.regPasswordContainer.setVisibility(View.VISIBLE);
        binding.regEmailContainer.setVisibility(View.VISIBLE);
        binding.registerButton.setVisibility(View.VISIBLE);

        // move the 'main' stuff out first
        hideMain(dur);

        // shift the logo up, by matching it's position with that of the placeholder
        int logoOffset = binding.registerLogoPlaceholder.getTop() - binding.logo.getTop();
        binding.logo.animate().setStartDelay(0).setDuration(dur).translationY(logoOffset);

        // bring in the registration fields
        binding.registerTitle.animate().setStartDelay(0).setDuration(dur).translationX(0);
        binding.registerSubtitle.animate().setStartDelay(100).setDuration(dur).translationX(0);
        binding.regUsernameContainer.animate().setStartDelay(200).setDuration(dur).translationX(0);
        binding.regPasswordContainer.animate().setStartDelay(300).setDuration(dur).translationX(0);
        binding.regEmailContainer.animate().setStartDelay(400).setDuration(dur).translationX(0);
        binding.registerButton.animate().setStartDelay(500).setDuration(dur).translationX(0);
    }

    //endregion

    interface Listener {
        @UiThread void onSignInAction();
        @UiThread void onRegisterAction();
    }
}
