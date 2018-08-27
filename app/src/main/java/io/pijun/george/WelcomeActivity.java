package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.security.SecureRandom;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import io.pijun.george.api.CreateUserResponse;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.databinding.ActivityWelcomeBinding;
import io.pijun.george.sodium.HashConfig;
import retrofit2.Response;

public class WelcomeActivity extends AppCompatActivity {

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, WelcomeActivity.class);
    }

    private ActivityWelcomeBinding binding;
    private WelcomeViewHolder viewHolder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_welcome);
        viewHolder = new WelcomeViewHolder(binding);

        binding.regUsername.addTextChangedListener(
                new UsernameWatcher(
                        binding.regUsernameContainer, binding.regUsername));

        binding.regPassword.addTextChangedListener(
                new PasswordWatcher(
                        binding.regPasswordContainer, binding.regPassword));

        binding.regEmail.addTextChangedListener(
                new EmailWatcher(
                        binding.regEmailContainer, binding.regEmail));

        binding.siUsername.addTextChangedListener(new ErrorDisabler(binding.siUsernameContainer));

        binding.siPassword.addTextChangedListener(new ErrorDisabler(binding.siPasswordContainer));
    }

    @Override
    protected void onStart() {
        super.onStart();

        viewHolder.setCloudMovementEnabled(true);
    }

    @Override
    protected void onStop() {
        super.onStop();

        viewHolder.setCloudMovementEnabled(false);
    }

    @Override
    public void onBackPressed() {
        WelcomeViewHolder.State state = viewHolder.getState();
        if (state == WelcomeViewHolder.State.Registration || state == WelcomeViewHolder.State.Login) {
            viewHolder.transitionToMain();
            return;
        }

        super.onBackPressed();
    }

    @UiThread
    public void onSignInAction(View v) {
        viewHolder.clearFocus();
        boolean foundError = false;

        final Editable usernameText = binding.siUsername.getText();
        if (TextUtils.isEmpty(usernameText)) {
            binding.siUsernameContainer.setError(getString(R.string.username_please_msg));
            foundError = true;
        }

        final Editable passwordText = binding.siPassword.getText();
        if(TextUtils.isEmpty(passwordText)) {
            binding.siPasswordContainer.setError(getString(R.string.password_missing_msg));
            foundError = true;
        }

        if (foundError) {
            return;
        }

        //noinspection ConstantConditions
        String username = usernameText.toString();
        //noinspection ConstantConditions
        String password = passwordText.toString();

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                login(username, password);
            }
        });
    }

    public void onShowRegistration(View v) {
        viewHolder.transitionToRegistration();
    }

    public void onShowSignIn(View v) {
        viewHolder.transitionToLogin();
    }

    @UiThread
    public void onRegisterAction(View v) {
        viewHolder.clearFocus();

        boolean foundError = false;
        final Editable usernameText = binding.regUsername.getText();
        int msgId = Utils.getInvalidUsernameReason(usernameText);
        if (msgId != 0) {
            binding.regUsernameContainer.setError(getString(msgId));
            foundError = true;
        }

        final Editable password = binding.siPassword.getText();
        if (password == null || password.length() < Constants.PASSWORD_TEXT_MIN_LENGTH) {
            binding.regPasswordContainer.setError(getString(R.string.too_short));
            foundError = true;
        }

        final Editable emailEditable = binding.regEmail.getText();
        if (emailEditable != null) {
            String email = emailEditable.toString().trim();
            if (!TextUtils.isEmpty(email)) {
                if (!Utils.isValidEmail(email)) {
                    binding.regEmailContainer.setError(getString(R.string.invalid_address));
                    foundError = true;
                }
            }
        }

        if (foundError) {
            return;
        }

        // This will always result in storing the username, because a null username gets checked
        // for up above.
        String username = usernameText != null ? usernameText.toString() : "";

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                final User user = generateUser(username, password.toString());
                if (user == null) {
                    Utils.showAlert(WelcomeActivity.this, 0, R.string.unknown_user_generation_error_msg);
                    return;
                }
                registerUser(user, password.toString());
            }
        });
    }

    @SuppressLint("WrongThread")
    @AnyThread
    void setLoginError(@NonNull final TextInputLayout textInputLayout, @StringRes final int msg) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                textInputLayout.setError(getString(msg));
            }
        });
    }

    @WorkerThread
    private void login(@NonNull final String username, @NonNull final String password) {
        AuthenticationManager.get().logIn(this, username, password, new AuthenticationManager.LoginWatcher() {
            @Override
            public void onUserLoggedIn(@NonNull AuthenticationManager.Error err, @Nullable String detail) {
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
                        setLoginError(binding.siPasswordContainer, R.string.incorrect_password);
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
                        setLoginError(binding.siUsernameContainer, R.string.unknown_username);
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
    public User generateUser(@NonNull String username, @NonNull String password) {
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
            Utils.showStringAlert(this, null, "Serious error creating account: " + e.getLocalizedMessage());
        }
    }

    @UiThread
    private void showMapActivity() {
        Intent i = MapActivity.newIntent(this);
        startActivity(i);
        finish();
    }

//    @Override
//    public void onWelcomeLayoutFocused(final View view) {
//        handleFocusChange(view, findViewById(R.id.scrollview), findViewById(R.id.root));
//    }

//    private void handleFocusChange(final View view, final ScrollView sv, final WelcomeLayout root) {
//        if (sv.getBottom() == root.getBottom()) {
//            // we need to give the window more time to present the keyboard and resize the scrollview
//            App.runOnUiThread(new UiRunnable() {
//                @Override
//                public void run() {
//                    handleFocusChange(view, sv, root);
//                }
//            }, 34);
//            return;
//        }
//        int id = view.getId();
//
//        View container;
//        switch (id) {
//            case R.id.reg_username:
//                container = root.findViewById(R.id.reg_username_container);
//                break;
//            case R.id.reg_password:
//                container = root.findViewById(R.id.reg_password_container);
//                break;
//            case R.id.reg_email:
//                container = root.findViewById(R.id.reg_email_container);
//                break;
//            case R.id.si_username:
//                container = root.findViewById(R.id.si_username_container);
//                break;
//            case R.id.si_password:
//                container = root.findViewById(R.id.si_password_container);
//                break;
//            default:
//                return;
//        }
//
//        int topMargin = (sv.getBottom() - container.getHeight()) / 2;
//        // The ScrollView's smoothScrollTo method is too fast and jerky, so we use a custom
//        // ValueAnimator to make the scroll prettier.
//        ValueAnimator animator = ValueAnimator.ofInt(sv.getScrollY(), container.getTop() - topMargin);
//        animator.setInterpolator(new Bezier65Interpolator());
//        animator.setDuration(500);
//        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//            @Override
//            public void onAnimationUpdate(ValueAnimator animation) {
//                sv.scrollTo(0, (int) animation.getAnimatedValue());
//            }
//        });
//        animator.start();
//    }

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
