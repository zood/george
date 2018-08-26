package io.pijun.george;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.security.SecureRandom;

import androidx.annotation.AnyThread;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import io.pijun.george.api.CreateUserResponse;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.interpolator.Bezier65Interpolator;
import io.pijun.george.sodium.HashConfig;
import retrofit2.Response;

public class WelcomeActivity extends AppCompatActivity implements WelcomeLayout.FocusListener {

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, WelcomeActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_welcome);
        final WelcomeLayout root = findViewById(R.id.root);

        TextInputLayout usernameC = root.findViewById(R.id.reg_username_container);
        TextInputEditText username = usernameC.findViewById(R.id.reg_username);
        username.addTextChangedListener(new UsernameWatcher(usernameC, username));

        TextInputLayout passwordC = root.findViewById(R.id.reg_password_container);
        TextInputEditText password = passwordC.findViewById(R.id.reg_password);
        password.addTextChangedListener(new PasswordWatcher(passwordC, password));

        TextInputLayout emailC = root.findViewById(R.id.reg_email_container);
        TextInputEditText email = emailC.findViewById(R.id.reg_email);
        email.addTextChangedListener(new EmailWatcher(emailC, email));

        TextInputLayout siUsernameC = root.findViewById(R.id.si_username_container);
        TextInputEditText siUsername = siUsernameC.findViewById(R.id.si_username);
        siUsername.addTextChangedListener(new ErrorDisabler(siUsernameC));

        TextInputLayout siPasswordC = root.findViewById(R.id.si_password_container);
        TextInputEditText siPassword = root.findViewById(R.id.si_password);
        siPassword.addTextChangedListener(new ErrorDisabler(siPasswordC));

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                root.transitionTo(WelcomeLayout.STATE_LOGO_AND_TITLES);
            }
        }, 1000);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final WelcomeLayout root = findViewById(R.id.root);
        if (root != null) {
            root.setCloudMovementEnabled(true);
            root.setFocusListener(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        final WelcomeLayout root = findViewById(R.id.root);
        if (root != null) {
            root.setCloudMovementEnabled(false);
        }
    }

    @Override
    public void onBackPressed() {
        WelcomeLayout root = findViewById(R.id.root);
        int state = root.getState();
        if (state == WelcomeLayout.STATE_REGISTER || state == WelcomeLayout.STATE_SIGN_IN) {
            root.transitionTo(WelcomeLayout.STATE_SPLASH);
            return;
        }

        super.onBackPressed();
    }

    @UiThread
    public void onSignInAction(View v) {
        boolean foundError = false;

        EditText usernameField = findViewById(R.id.si_username);
        final String username = usernameField.getText().toString();
        if (TextUtils.isEmpty(username)) {
            TextInputLayout til = findViewById(R.id.si_username_container);
            til.setError(getString(R.string.username_please_msg));
            foundError = true;
        }

        EditText passwordField = findViewById(R.id.si_password);
        final String password = passwordField.getText().toString();
        if(TextUtils.isEmpty(password)) {
            TextInputLayout til = findViewById(R.id.si_password_container);
            til.setError(getString(R.string.password_missing_msg));
            foundError = true;
        }

        if (foundError) {
            return;
        }

        setBusy(true);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                login(username, password);
            }
        });
    }

    public void onShowRegistration(View v) {
        WelcomeLayout root = findViewById(R.id.root);
        root.transitionTo(WelcomeLayout.STATE_REGISTER);
    }

    public void onShowSignIn(View v) {
        WelcomeLayout root = findViewById(R.id.root);
        root.transitionTo(WelcomeLayout.STATE_SIGN_IN);
    }

    @UiThread
    public void onRegisterAction(View v) {
        boolean foundError = false;
        final EditText usernameField = findViewById(R.id.reg_username);
        final String username = usernameField.getText().toString();
        int msgId = Utils.getInvalidUsernameReason(username);
        if (msgId != 0) {
            TextInputLayout til = findViewById(R.id.reg_username_container);
            til.setError(getString(msgId));
            foundError = true;
        }

        final EditText passwordField = findViewById(R.id.reg_password);
        final String password = passwordField.getText().toString();
        if (password.length() < Constants.PASSWORD_TEXT_MIN_LENGTH) {
            TextInputLayout til = findViewById(R.id.reg_password_container);
            til.setError(getString(R.string.too_short));
            foundError = true;
        }

        final EditText emailField = findViewById(R.id.reg_email);
        final String email = emailField.getText().toString().trim();
        if (!TextUtils.isEmpty(email)) {
            if (!Utils.isValidEmail(email)) {
                TextInputLayout til = findViewById(R.id.reg_email_container);
                til.setError(getString(R.string.invalid_address));
                foundError = true;
            }
        }

        if (foundError) {
            return;
        }

        setBusy(true);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                final User user = generateUser(username, password);
                if (user == null) {
                    Utils.showAlert(WelcomeActivity.this, 0, R.string.unknown_user_generation_error_msg);
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            setBusy(false);
                        }
                    });
                    return;
                }
                registerUser(user, password);
            }
        });
    }

    @SuppressLint("WrongThread")
    @AnyThread
    void setLoginError(@IdRes final int textInputLayout, @StringRes final int msg) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                _setLoginError(textInputLayout, msg);
            }
        });
    }

    @UiThread
    void _setLoginError(@IdRes int textInputLayout, @StringRes int msg) {
        if (textInputLayout != R.id.si_password_container && textInputLayout != R.id.si_username_container) {
            throw new IllegalArgumentException("Incorrect textinputlayout used for setLoginError");
        }
        TextInputLayout layout = findViewById(textInputLayout);
        layout.setError(getString(msg));
    }

    @WorkerThread
    private void login(final String username, final String password) {
        AuthenticationManager.get().logIn(this, username, password, new AuthenticationManager.LoginWatcher() {
            @Override
            public void onUserLoggedIn(@NonNull AuthenticationManager.Error err, @Nullable String detail) {
                setBusy(false);
                switch (err) {
                    case None:
                        showMapActivity();
                        break;
                    case AuthenticationChallengeFailed:
                        Utils.showStringAlert(WelcomeActivity.this,
                                getString(R.string.login_failed),
                                detail != null ? detail : getString(R.string.unknown_error_completing_challenge_response_msg));
                        break;
                    case DatabaseBackupDecryptionFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to restore profile. Did your symmetric key change?");
                        break;
                    case DatabaseBackupParsingFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to decode your profile");
                        break;
                    case DatabaseRestoreFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to rebuild your profile. This is a bug and it has been reported to our engineers.");
                        break;
                    case AuthenticationChallengeCreationFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to start log in process: " + detail);
                        break;
                    case IncorrectPassword:
                        setLoginError(R.id.si_password_container, R.string.incorrect_password);
                        break;
                    case MalformedAuthenticationChallengeResponse:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Server returned a malformed authentication challenge. Try again later, and contact support if the problem persists.");
                        break;
                    case MalformedDatabaseBackupResponse:
                        Utils.showStringAlert(WelcomeActivity.this, null, "The server returned a malformed response when retrieving your profile. Try again later, and if it still fails, contact support.");
                        break;
                    case MalformedLoginResponse:
                        Utils.showStringAlert(WelcomeActivity.this,
                                null,
                                "The server returned a malformed login response. Try again later, and if it continues, contact support.");
                        break;
                    case MalformedServerKeyResponse:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.server_key_retrieval_bad_response_msg);
                        break;
                    case NetworkErrorCompletingAuthenticationChallenge:
                        Utils.showAlert(WelcomeActivity.this, R.string.network_error, R.string.login_failure_network_msg);
                        break;
                    case NetworkErrorRetrievingDatabaseBackup:
                        Utils.showAlert(WelcomeActivity.this, R.string.login_failed, R.string.network_failure_profile_restore_msg);
                        break;
                    case NetworkErrorRetrievingServerKey:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.server_key_retrieval_network_error_msg);
                        break;
                    case NullPasswordHash:
                        CloudLogger.log("Password was null");
                        Utils.showAlert(WelcomeActivity.this, R.string.unexpected_error, R.string.null_password_hash_msg);
                        break;
                    case OutdatedClient:
                        Utils.showStringAlert(WelcomeActivity.this, null, "This version of Pijun is too old. Update to the latest version then try logging in again.");
                        break;
                    case UserNotFound:
                        setLoginError(R.id.si_username_container, R.string.unknown_username);
                        break;
                    case UnknownErrorRetrievingDatabaseBackup:
                        Utils.showAlert(WelcomeActivity.this, R.string.login_failed, R.string.unknown_failure_profile_restore_msg);
                        break;
                    case UnknownErrorRetrievingServerKey:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.server_key_retrieval_unknown_error_msg);
                        break;
                    case UnknownPasswordHashAlgorithm:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.unknown_password_hash_algorithm_msg);
                        break;
                    case SymmetricKeyDecryptionFailed:
                        Utils.showAlert(WelcomeActivity.this, R.string.login_failed, R.string.symmetric_key_unwrap_failure_msg);
                        break;
                    case Unknown:
                        default:
                            Utils.showStringAlert(WelcomeActivity.this, null, "Unknown error");
                            break;
                }
            }
        });
    }

    @WorkerThread
    @Nullable
    public User generateUser(String username, String password) {
        L.i("generateUser");
        User u = new User();
        u.username = username;
        KeyPair kp = new KeyPair();
        int result = Sodium.generateKeyPair(kp);
        if (result != 0) {
            Utils.showAlert(this, 0, R.string.keypair_generation_error);
            L.i("failed to generate key pair");
            return null;
        }
        Prefs prefs = Prefs.get(this);
        prefs.setKeyPair(kp);
        u.publicKey = kp.publicKey;

        HashConfig hashCfg = new HashConfig(HashConfig.Algorithm.Argon2id13, HashConfig.OpsSecurity.Sensitive, HashConfig.MemSecurity.Moderate);
        u.passwordHashAlgorithm = hashCfg.alg.name;
        u.passwordHashOperationsLimit = hashCfg.getOpsLimit();
        u.passwordHashMemoryLimit = hashCfg.getMemLimit();

        u.passwordSalt = new byte[hashCfg.alg.saltLength];
        new SecureRandom().nextBytes(u.passwordSalt);

        // We need a key with which to encrypt our data, so we'll use the hash of our password
        byte[] passwordHash = Sodium.stretchPassword(Sodium.getSymmetricKeyLength(),
                password.getBytes(Constants.utf8),
                u.passwordSalt,
                hashCfg.alg.sodiumId,
                hashCfg.getOpsLimit(),
                hashCfg.getMemLimit());
        if (passwordHash == null) {
            L.i("password was null when generating user");
            return null;
        }
        prefs.setPasswordSalt(u.passwordSalt);

        byte[] symmetricKey = new byte[Sodium.getSymmetricKeyLength()];
        new SecureRandom().nextBytes(symmetricKey);
        prefs.setSymmetricKey(symmetricKey);

        EncryptedData wrappedSymmetricKey = Sodium.symmetricKeyEncrypt(symmetricKey, passwordHash);
        if (wrappedSymmetricKey == null) {
            L.i("wrapped sym key null when generating user");
            return null;
        }
        u.wrappedSymmetricKey = wrappedSymmetricKey.cipherText;
        u.wrappedSymmetricKeyNonce = wrappedSymmetricKey.nonce;

        EncryptedData wrappedSecretKey = Sodium.symmetricKeyEncrypt(kp.secretKey, passwordHash);
        if (wrappedSecretKey == null) {
            L.i("wrapped sec key null when generating user");
            return null;
        }
        u.wrappedSecretKey = wrappedSecretKey.cipherText;
        u.wrappedSecretKeyNonce = wrappedSecretKey.nonce;

        return u;
    }

    @WorkerThread
    private void registerUser(User user, String password) {
        OscarAPI api = OscarClient.newInstance(null);
        try {
            final Response<CreateUserResponse> response = api.createUser(user).execute();
            setBusy(false);
            if (response.isSuccessful()) {
                CreateUserResponse resp = response.body();
                if (resp == null) {
                    Utils.showStringAlert(this, null, "The server returned a malformed response when creating your account. Try again later or contact support if the problem continues.");
                    return;
                }
                Prefs prefs = Prefs.get(this);
                prefs.setUserId(resp.id);
                prefs.setUsername(user.username);

                login(user.username, password);
            } else {
                OscarError err = OscarError.fromResponse(response);
                if (err != null) {
                    if (err.code == OscarError.ERROR_USERNAME_NOT_AVAILABLE) {
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.username_not_available_msg);
                    } else {
                        Utils.showStringAlert(WelcomeActivity.this, null, err.message);
                    }
                } else {
                    Utils.showStringAlert(WelcomeActivity.this, null, "unknown error occurred when attempting to register account");
                }
            }
        } catch (IOException e) {
            setBusy(false);
            Utils.showStringAlert(this, null, "Serious error creating account: " + e.getLocalizedMessage());
        }
    }

    @UiThread
    private void showMapActivity() {
        Intent i = MapActivity.newIntent(this);
        startActivity(i);
        finish();
    }

    @Override
    public void onWelcomeLayoutFocused(final View view) {
        handleFocusChange(view, findViewById(R.id.scrollview), findViewById(R.id.root));
    }

    private void handleFocusChange(final View view, final ScrollView sv, final WelcomeLayout root) {
        if (sv.getBottom() == root.getBottom()) {
            // we need to give the window more time to present the keyboard and resize the scrollview
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    handleFocusChange(view, sv, root);
                }
            }, 34);
            return;
        }
        int id = view.getId();

        View container;
        switch (id) {
            case R.id.reg_username:
                container = root.findViewById(R.id.reg_username_container);
                break;
            case R.id.reg_password:
                container = root.findViewById(R.id.reg_password_container);
                break;
            case R.id.reg_email:
                container = root.findViewById(R.id.reg_email_container);
                break;
            case R.id.si_username:
                container = root.findViewById(R.id.si_username_container);
                break;
            case R.id.si_password:
                container = root.findViewById(R.id.si_password_container);
                break;
            default:
                return;
        }

        int topMargin = (sv.getBottom() - container.getHeight()) / 2;
        // The ScrollView's smoothScrollTo method is too fast and jerky, so we use a custom
        // ValueAnimator to make the scroll prettier.
        ValueAnimator animator = ValueAnimator.ofInt(sv.getScrollY(), container.getTop() - topMargin);
        animator.setInterpolator(new Bezier65Interpolator());
        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                sv.scrollTo(0, (int) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    @UiThread
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        imm.hideSoftInputFromWindow(findViewById(R.id.root).getWindowToken(), 0);
    }

    @SuppressLint("WrongThread")
    @AnyThread
    private void setBusy(final boolean b) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _setBusy(b);
        } else {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    _setBusy(b);
                }
            });
        }
    }

    @UiThread
    private void _setBusy(boolean b) {
        WelcomeLayout root = findViewById(R.id.root);
        root.requestFocus();
        if (root.getState() == WelcomeLayout.STATE_REGISTER) {
            root.setRegistrationSpinnerVisible(b);
            root.findViewById(R.id.reg_password).setEnabled(!b);
            root.findViewById(R.id.reg_username).setEnabled(!b);
            root.findViewById(R.id.reg_email).setEnabled(!b);
            hideKeyboard();
        } else if (root.getState() == WelcomeLayout.STATE_SIGN_IN) {
            root.setSignInSpinnerVisible(b);
            root.findViewById(R.id.si_username).setEnabled(!b);
            root.findViewById(R.id.si_password).setEnabled(!b);
            hideKeyboard();
        }
    }

    private abstract class StandardWatcher implements TextWatcher {

        TextInputLayout mLayout;
        TextInputEditText mEditText;

        StandardWatcher(@NonNull TextInputLayout layout, @NonNull TextInputEditText editText) {
            mLayout = layout;
            mEditText = editText;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private class UsernameWatcher extends StandardWatcher {
        UsernameWatcher(@NonNull TextInputLayout layout, @NonNull TextInputEditText editText) {
            super(layout, editText);
        }

        @Override
        public void afterTextChanged(Editable s) {
            mLayout.setErrorEnabled(false);
            if (s != null) {
                if (Utils.isValidUsername(s.toString())) {
                    mEditText.setSelected(true);
                    return;
                }
            }
            mEditText.setSelected(false);
        }
    }

    private class PasswordWatcher extends StandardWatcher {
        PasswordWatcher(@NonNull TextInputLayout layout, @NonNull TextInputEditText editText) {
            super(layout, editText);
        }

        @Override
        public void afterTextChanged(Editable s) {
            mLayout.setErrorEnabled(false);
            if (s != null) {
                if (s.length() >= Constants.PASSWORD_TEXT_MIN_LENGTH) {
                    mEditText.setSelected(true);
                    return;
                }
            }
            mEditText.setSelected(false);
        }
    }

    private class EmailWatcher extends StandardWatcher {
        EmailWatcher(@NonNull TextInputLayout layout, @NonNull TextInputEditText editText) {
            super(layout, editText);
        }

        @Override
        public void afterTextChanged(Editable s) {
            mLayout.setErrorEnabled(false);
            if (s != null) {
                if (Utils.isValidEmail(s.toString())) {
                    mEditText.setSelected(true);
                    return;
                }
            }
            mEditText.setSelected(false);
        }
    }

    private class ErrorDisabler implements TextWatcher {
        private TextInputLayout mLayout;
        ErrorDisabler(@NonNull TextInputLayout layout) {
            mLayout = layout;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            mLayout.setErrorEnabled(false);
        }
    }
}
