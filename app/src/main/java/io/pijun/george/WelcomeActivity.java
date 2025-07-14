package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.security.SecureRandom;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentManager;
import io.pijun.george.api.CreateUserResponse;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.sodium.HashConfig;
import retrofit2.Response;
import xyz.zood.george.MainActivity;
import xyz.zood.george.R;
import xyz.zood.george.databinding.ActivityWelcomeBinding;
import xyz.zood.george.widget.AuthenticationProgressDialog;

public class WelcomeActivity extends AppCompatActivity implements WelcomeViewHolder.Listener {

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, WelcomeActivity.class);
    }

    private ActivityWelcomeBinding binding;
    private WelcomeViewHolder viewHolder;
    @Nullable private AuthenticationProgressDialog progressDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_welcome);
        viewHolder = new WelcomeViewHolder(binding, this);

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WelcomeViewHolder.State state = viewHolder.getState();
                if (state == WelcomeViewHolder.State.Registration || state == WelcomeViewHolder.State.Login) {
                    viewHolder.transitionToMain();
                } else {
                    finish();
                }
            }
        });

        WindowCompat.getInsetsController(getWindow(), binding.root).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.root).setAppearanceLightNavigationBars(true);
    }

    public void onShowRegistration(View v) {
        viewHolder.transitionToRegistration();
    }

    public void onShowSignIn(View v) {
        viewHolder.transitionToLogin();
    }

    @UiThread
    public void onRegisterAction(View v) {
        onRegisterAction();
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
                showProgressDialog(false);
                FragmentManager fm = getSupportFragmentManager();
                switch (err) {
                    case None:
                        showMapActivity();
                        break;
                    case AuthenticationChallengeFailed:
                        Utils.showStringAlert(WelcomeActivity.this,
                                getString(R.string.login_failed),
                                detail != null ? detail : getString(R.string.unknown_error_completing_challenge_response_msg), fm);
                        break;
                    case DatabaseBackupDecryptionFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to restore profile. Did your symmetric key change?", fm);
                        break;
                    case DatabaseBackupParsingFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to decode your profile", fm);
                        break;
                    case DatabaseRestoreFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to rebuild your profile. This is a bug and it has been reported to our engineers.", fm);
                        break;
                    case AuthenticationChallengeCreationFailed:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Unable to start log in process: " + detail, fm);
                        break;
                    case IncorrectPassword:
                        setLoginError(binding.siPasswordContainer, R.string.incorrect_password);
                        break;
                    case MalformedAuthenticationChallengeResponse:
                        Utils.showStringAlert(WelcomeActivity.this, null, "Server returned a malformed authentication challenge. Try again later, and contact support if the problem persists.", fm);
                        break;
                    case MalformedDatabaseBackupResponse:
                        Utils.showStringAlert(WelcomeActivity.this, null, "The server returned a malformed response when retrieving your profile. Try again later, and if it still fails, contact support.", fm);
                        break;
                    case MalformedLoginResponse:
                        Utils.showStringAlert(WelcomeActivity.this,
                                null,
                                "The server returned a malformed login response. Try again later, and if it continues, contact support.", fm);
                        break;
                    case MalformedServerKeyResponse:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.server_key_retrieval_bad_response_msg, fm);
                        break;
                    case NetworkErrorCompletingAuthenticationChallenge:
                        Utils.showAlert(WelcomeActivity.this, R.string.network_error, R.string.login_failure_network_msg, fm);
                        break;
                    case NetworkErrorRetrievingDatabaseBackup:
                        Utils.showAlert(WelcomeActivity.this, R.string.login_failed, R.string.network_failure_profile_restore_msg, fm);
                        break;
                    case NetworkErrorRetrievingServerKey:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.server_key_retrieval_network_error_msg, fm);
                        break;
                    case NullPasswordHash:
                        CloudLogger.log("Password was null");
                        Utils.showAlert(WelcomeActivity.this, R.string.unexpected_error, R.string.null_password_hash_msg, fm);
                        break;
                    case OutdatedClient:
                        Utils.showStringAlert(WelcomeActivity.this, null, "This version of Pijun is too old. Update to the latest version then try logging in again.", fm);
                        break;
                    case UserNotFound:
                        setLoginError(binding.siUsernameContainer, R.string.unknown_username);
                        break;
                    case UnknownErrorRetrievingDatabaseBackup:
                        Utils.showAlert(WelcomeActivity.this, R.string.login_failed, R.string.unknown_failure_profile_restore_msg, fm);
                        break;
                    case UnknownErrorRetrievingServerKey:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.server_key_retrieval_unknown_error_msg, fm);
                        break;
                    case UnknownPasswordHashAlgorithm:
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.unknown_password_hash_algorithm_msg, fm);
                        break;
                    case SymmetricKeyDecryptionFailed:
                        Utils.showAlert(WelcomeActivity.this, R.string.login_failed, R.string.symmetric_key_unwrap_failure_msg, fm);
                        break;
                    case Unknown:
                    default:
                       Utils.showStringAlert(WelcomeActivity.this, null, "Unknown error", fm);
                       break;
                }
            }
        });
    }

    @WorkerThread
    @Nullable
    private User generateUser(@NonNull String username, @NonNull String password, @Nullable String email) {
        L.i("generateUser");
        User u = new User();
        u.username = username;
        u.email = email;
        KeyPair kp = new KeyPair();
        int result = Sodium.generateKeyPair(kp);
        if (result != 0) {
            Utils.showAlert(this, 0, R.string.keypair_generation_error, getSupportFragmentManager());
            L.i("failed to generate key pair");
            return null;
        }
        Prefs prefs = Prefs.get(this);
        prefs.setKeyPair(kp);
        u.publicKey = kp.publicKey;

        HashConfig hashCfg = new HashConfig(HashConfig.Algorithm.Argon2id13, HashConfig.OpsSecurity.ZoodSensitive, HashConfig.MemSecurity.Interactive);
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
        FragmentManager fm = getSupportFragmentManager();
        try {
            final Response<CreateUserResponse> response = api.createUser(user).execute();
            if (response.isSuccessful()) {
                CreateUserResponse resp = response.body();
                if (resp == null) {
                    showProgressDialog(false);
                    Utils.showStringAlert(this, null, "The server returned a malformed response when creating your account. Try again later or contact support if the problem continues.", fm);
                    return;
                }
                Prefs prefs = Prefs.get(this);
                prefs.setUserId(resp.id);
                prefs.setUsername(user.username);

                login(user.username, password);
            } else {
                showProgressDialog(false);
                OscarError err = OscarError.fromResponse(response);
                if (err != null) {
                    if (err.code == OscarError.ERROR_USERNAME_NOT_AVAILABLE) {
                        Utils.showAlert(WelcomeActivity.this, 0, R.string.username_not_available_msg, fm);
                    } else {
                        Utils.showStringAlert(WelcomeActivity.this, null, err.message, fm);
                    }
                } else {
                    Utils.showStringAlert(WelcomeActivity.this, null, "unknown error occurred when attempting to register account", fm);
                }
            }
        } catch (IOException e) {
            showProgressDialog(false);
            Utils.showStringAlert(this, null, "Serious error creating account: " + e.getLocalizedMessage(), fm);
        }
    }

    @UiThread
    private void showMapActivity() {
        Intent i = MainActivity.newIntent(this);
        startActivity(i);
        finish();
    }

    @UiThread
    private void _showProgressDialog(boolean shouldShow) {
        if (shouldShow) {
            // check if we're already showing it
            if (progressDialog != null) {
                return;
            }

            progressDialog = new AuthenticationProgressDialog();
            progressDialog.show(getSupportFragmentManager(), null);
        } else {
            // Make sure we're actually showing the dialog
            if (progressDialog == null) {
                return;
            }
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @SuppressLint("WrongThread")
    @AnyThread
    private void showProgressDialog(boolean shouldShow) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            _showProgressDialog(shouldShow);
        } else {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    _showProgressDialog(shouldShow);
                }
            });
        }

    }

    public void onSignInAction(View v) {
        onSignInAction();
    }

    //region WelcomeViewHolder.Listener

    @Override
    public void onRegisterAction() {
        viewHolder.clearFocus();

        boolean foundError = false;
        final Editable usernameText = binding.regUsername.getText();
        int msgId = Utils.getInvalidUsernameReason(usernameText);
        if (msgId != 0) {
            binding.regUsernameContainer.setError(getString(msgId));
            foundError = true;
        }

        final Editable password = binding.regPassword.getText();
        if (password == null || password.length() < Constants.PASSWORD_TEXT_MIN_LENGTH) {
            binding.regPasswordContainer.setError(getString(R.string.too_short));
            foundError = true;
        }

        final Editable emailEditable = binding.regEmail.getText();
        String emailTmp = null;
        if (emailEditable != null) {
            String txt = emailEditable.toString().trim();
            if (!TextUtils.isEmpty(txt)) {
                if (!Utils.isValidEmail(txt)) {
                    binding.regEmailContainer.setError(getString(R.string.invalid_address));
                    foundError = true;
                } else {
                    emailTmp = txt;
                }
            }
        }

        if (foundError) {
            return;
        }

        // This will always result in storing the username, because a null username gets checked
        // for up above.
        String username = usernameText != null ? usernameText.toString() : "";
        final String email = emailTmp;

        showProgressDialog(true);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                final User user = generateUser(username, password.toString(), email);
                if (user == null) {
                    showProgressDialog(false);
                    Utils.showAlert(WelcomeActivity.this, 0, R.string.unknown_user_generation_error_msg, getSupportFragmentManager());
                    return;
                }
                registerUser(user, password.toString());
            }
        });
    }

    @UiThread
    @Override
    public void onSignInAction() {
        viewHolder.clearFocus();
        boolean foundError = false;

        final Editable usernameText = binding.siUsername.getText();
        if (usernameText == null || TextUtils.isEmpty(usernameText)) {
            binding.siUsernameContainer.setError(getString(R.string.username_please_msg));
            foundError = true;
        }

        final Editable passwordText = binding.siPassword.getText();
        if(passwordText == null || TextUtils.isEmpty(passwordText)) {
            binding.siPasswordContainer.setError(getString(R.string.password_missing_msg));
            foundError = true;
        }

        if (foundError) {
            return;
        }

        String username = usernameText.toString().trim();
        String password = passwordText.toString();

        showProgressDialog(true);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                login(username, password);
            }
        });
    }

    //endregion

    //region TextWatchers

    private abstract static class StandardWatcher implements TextWatcher {

        final TextInputLayout mLayout;
        final TextInputEditText mEditText;

        StandardWatcher(@NonNull TextInputLayout layout, @NonNull TextInputEditText editText) {
            mLayout = layout;
            mEditText = editText;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static class UsernameWatcher extends StandardWatcher {
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

    private static class PasswordWatcher extends StandardWatcher {
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

    private static class EmailWatcher extends StandardWatcher {
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

    private static class ErrorDisabler implements TextWatcher {
        private final TextInputLayout mLayout;
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

    //endregion
}
