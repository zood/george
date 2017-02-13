package io.pijun.george;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.google.firebase.crash.FirebaseCrash;

import java.io.IOException;
import java.security.SecureRandom;

import io.pijun.george.api.AuthenticationChallenge;
import io.pijun.george.api.CreateUserResponse;
import io.pijun.george.api.FinishedAuthenticationChallenge;
import io.pijun.george.api.LoginResponse;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.ServerPublicKeyResponse;
import io.pijun.george.api.User;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.models.Snapshot;
import retrofit2.Response;

public class WelcomeActivity extends AppCompatActivity {

    private boolean mShowingCreateAccount;
    private boolean mShowingSignIn;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, WelcomeActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShowingCreateAccount = false;
        mShowingSignIn = false;
        setContentView(R.layout.activity_welcome);
        ConstraintLayout root = (ConstraintLayout) findViewById(R.id.constraintLayout);
        LayoutTransition lt = new LayoutTransition();
        lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        lt.disableTransitionType(LayoutTransition.DISAPPEARING);
        lt.disableTransitionType(LayoutTransition.APPEARING);
        lt.enableTransitionType(LayoutTransition.CHANGING);
        root.setLayoutTransition(lt);
    }

    @Override
    public void onBackPressed() {
        if (mShowingCreateAccount) {
            mShowingCreateAccount = false;
            hideFields(R.id.create_account_container);
            return;
        } else if (mShowingSignIn) {
            mShowingSignIn = false;
            hideFields(R.id.sign_in_container);
            return;
        }

        super.onBackPressed();
    }

    @UiThread
    private void inflateAndPresent(@LayoutRes int layoutId) {
        if (layoutId != R.layout.create_account_fields && layoutId != R.layout.sign_in_fields) {
            throw new IllegalArgumentException("What the heck are you doing?");
        }

        // shrink and move the title up
        View title = findViewById(R.id.screen_title);
        title.animate()
                .scaleY(.5f)
                .scaleX(.5f)
                .start();

        // fade out the subtitle
        View subtitle = findViewById(R.id.screen_subtitle);
        subtitle.animate().alpha(0).start();

        // inflate all our fields
        ViewGroup root = (ViewGroup) findViewById(R.id.constraintLayout);
        final View fieldsView = getLayoutInflater().inflate(layoutId, root, false);
        fieldsView.setTranslationX(root.getWidth());
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT);
        // configure the parameters for the fields then add them to the hierarchy
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        root.addView(fieldsView);
//        root.addView(fieldsView, params);

        // have the keyboard focus on the username field
        final EditText usernameField = (EditText) fieldsView.findViewById(R.id.username_field);
        showKeyboard(usernameField);
        // animate the fields into view
        fieldsView.animate().translationX(0).start();

        // hide the 'get started' and 'sign in' buttons
        final View getStartedButton = findViewById(R.id.get_started_button);
        getStartedButton.animate().translationX(-root.getWidth()).start();
        final View signInButton = findViewById(R.id.sign_in_button);
        signInButton.animate().translationX(-root.getWidth()).start();
    }

    @UiThread
    public void onGetStartedAction(View v) {
        mShowingCreateAccount = true;

        inflateAndPresent(R.layout.create_account_fields);
    }

    @UiThread
    public void onSignInAction(View v) {
        mShowingSignIn = true;

        inflateAndPresent(R.layout.sign_in_fields);
    }

    @UiThread
    public void onTogglePasswordVisibility(View v) {
        EditText field = (EditText) findViewById(R.id.password_field);
        L.i("on password toggle: " + field.getInputType());
        int basicPassword = InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT;
        if (field.getInputType() == basicPassword) {
            field.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            field.setInputType(basicPassword);
        }
    }

    @UiThread
    public void onCreateAccountAction(View v) {
        EditText usernameField = (EditText) findViewById(R.id.username_field);
        final String username = usernameField.getText().toString();
        if (TextUtils.isEmpty(username)) {
            Utils.showAlert(this, 0, R.string.need_username_msg);
            return;
        }

        EditText passwordField = (EditText) findViewById(R.id.password_field);
        final String password = passwordField.getText().toString();
        if (password.length() < 6) {
            Utils.showAlert(this, 0, R.string.password_too_short_msg);
            return;
        }

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                final User user = generateUser(username, password);
                if (user == null) {
                    Utils.showAlert(WelcomeActivity.this, 0, R.string.unknown_user_generation_error_msg);
                    return;
                }
                registerUser(user, password);
            }
        });
    }

    @UiThread
    public void onLoginAction(View v) {
        EditText usernameField = (EditText) findViewById(R.id.username_field);
        final String username = usernameField.getText().toString();
        if (TextUtils.isEmpty(username)) {
            Utils.showAlert(this, 0, R.string.enter_username_msg);
        }

        EditText passwordField = (EditText) findViewById(R.id.password_field);
        final String password = passwordField.getText().toString();

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                login(username, password);
            }
        });
    }

    @UiThread
    private void hideFields(@IdRes int containerId) {
        if (containerId != R.id.create_account_container && containerId != R.id.sign_in_container) {
            throw new IllegalArgumentException("Use your brain");
        }

        hideKeyboard();

        // rescale+reposition the title and subtitle
        View title = findViewById(R.id.screen_title);
        title.requestLayout();
        title.animate().scaleY(1.0f).scaleX(1.0f).start();
        View subtitle = findViewById(R.id.screen_subtitle);
        subtitle.animate().alpha(1).start();

        // animate out the fields and then remove them
        final ViewGroup root = (ViewGroup) findViewById(R.id.constraintLayout);
        final View fields = findViewById(containerId);
        fields.animate().translationX(root.getWidth()).withEndAction(new Runnable() {
            @Override
            public void run() {
                root.removeView(fields);
            }
        }).start();

        // bring the bottom buttons back
        final View getStartedButton = findViewById(R.id.get_started_button);
        getStartedButton.animate().translationX(0).start();
        final View signInButton = findViewById(R.id.sign_in_button);
        signInButton.animate().translationX(0).start();
    }

    @WorkerThread
    public void login(final String username, final String password) {
        L.i("logging in");
        OscarAPI api = OscarClient.newInstance(null);
        Response<AuthenticationChallenge> startChallengeResp;
        try {
            startChallengeResp = api.getAuthenticationChallenge(username).execute();
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Unable to start log in process");
            return;
        }

        if (!startChallengeResp.isSuccessful()) {
            OscarError err = OscarError.fromResponse(startChallengeResp);
            if (err != null && err.code == OscarError.ERROR_USER_NOT_FOUND) {
                Utils.showStringAlert(WelcomeActivity.this, null, "Unknown user");
            } else {
                Utils.showStringAlert(WelcomeActivity.this, null, "Unknown error");
            }
            return;
        }
        AuthenticationChallenge authChallenge = startChallengeResp.body();

        final byte[] passwordHash = Sodium.createHashFromPassword(
                Sodium.getSymmetricKeyLength(),
                password.getBytes(),
                authChallenge.user.passwordSalt,
                authChallenge.user.passwordHashOperationsLimit,
                authChallenge.user.passwordHashMemoryLimit);

        // now try to decrypt the private key
        final byte[] secretKey = Sodium.symmetricKeyDecrypt(
                authChallenge.user.wrappedSecretKey,
                authChallenge.user.wrappedSecretKeyNonce,
                passwordHash);

        if (secretKey == null) {
            Utils.showAlert(this, R.string.incorrect_password, 0);
            return;
        }

        // grab the server's public key
        Response<ServerPublicKeyResponse> pubKeyResp;
        try {
            pubKeyResp = api.getServerPublicKey().execute();
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Unable to retrieve server's public key (network error?)");
            return;
        }
        if (!pubKeyResp.isSuccessful()) {
            Utils.showStringAlert(this, null, "Unknown error retrieving server's public key");
            return;
        }
        byte[] pubKey = pubKeyResp.body().publicKey;

        FinishedAuthenticationChallenge finishedChallenge = new FinishedAuthenticationChallenge();
        finishedChallenge.challenge = Sodium.publicKeyEncrypt(authChallenge.challenge, pubKey, secretKey);
        finishedChallenge.creationDate = Sodium.publicKeyEncrypt(authChallenge.creationDate, pubKey, secretKey);

        Response<LoginResponse> completeChallengeResp;
        try {
            completeChallengeResp = api.completeAuthenticationChallenge(username, finishedChallenge).execute();
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Unable to complete authentication challenge");
            return;
        }
        if (!completeChallengeResp.isSuccessful()) {
            Utils.showStringAlert(this, null, "Login failed: " + OscarError.fromResponse(completeChallengeResp));
            return;
        }

        final LoginResponse loginResponse = completeChallengeResp.body();
        byte[] symmetricKey = Sodium.symmetricKeyDecrypt(loginResponse.wrappedSymmetricKey,
                loginResponse.wrappedSymmetricKeyNonce,
                passwordHash);
        if (symmetricKey == null) {
            Utils.showStringAlert(this, null, "Login failed. Unable to unwrap your symmetric key");
            return;
        }

        // retrieve and restore the database backup
        api = OscarClient.newInstance(loginResponse.accessToken);
        Response<EncryptedData> dbResponse;
        try {
            dbResponse = api.getDatabaseBackup().execute();

        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Unable to restore profile. Check your connection then try again.");
            return;
        }
        if (!dbResponse.isSuccessful()) {
            OscarError err = OscarError.fromResponse(dbResponse);
            if (err == null || err.code != OscarError.ERROR_BACKUP_NOT_FOUND) {
                Utils.showStringAlert(this, null, "Unknown error retrieving profile: " + dbResponse.code());
                return;
            }
            // If we get here, that means no backup was found. It's weird, but not strictly wrong.
        } else {
            EncryptedData encSnapshot = dbResponse.body();
            byte[] jsonDb = Sodium.symmetricKeyDecrypt(encSnapshot.cipherText, encSnapshot.nonce, symmetricKey);
            if (jsonDb == null) {
                Utils.showStringAlert(this, null, "Unable to restore profile. Did your key change?");
                return;
            }
            Snapshot snapshot = Snapshot.fromJson(jsonDb);
            // restore the database
            if (snapshot == null) {
                Utils.showStringAlert(this, null, "Unable to decode your profile");
                return;
            }

            if (snapshot.schemaVersion > DB.get(this).getSchemaVersion()) {
                Utils.showStringAlert(this, null, "This version of Pijun is too old. Update to the latest version then try logging in again.");
                return;
            }

            try {
                DB.get(this).restoreDatabase(snapshot);
            } catch (DB.DBException ex) {
                FirebaseCrash.report(ex);
                Utils.showStringAlert(this, null, "Unable to rebuild your profile. This is a bug.");
                return;
            }
        }

        Prefs prefs = Prefs.get(this);
        prefs.setSymmetricKey(symmetricKey);
        prefs.setPasswordSalt(authChallenge.user.passwordSalt);
        KeyPair kp = new KeyPair();
        kp.publicKey = authChallenge.user.publicKey;
        kp.secretKey = secretKey;
        prefs.setKeyPair(kp);
        prefs.setUserId(loginResponse.id);
        prefs.setUsername(username);
        prefs.setAccessToken(loginResponse.accessToken);

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                showMapActivity();
            }
        });
    }

    @WorkerThread
    public User generateUser(String username, String password) {
        L.i("generateUser");
        User u = new User();
        u.username = username;
        KeyPair kp = new KeyPair();
        int result = Sodium.generateKeyPair(kp);
        if (result != 0) {
            Utils.showAlert(this, 0, R.string.keypair_generation_error);
            return null;
        }
        Prefs prefs = Prefs.get(this);
        prefs.setKeyPair(kp);
        u.publicKey = kp.publicKey;

        u.passwordSalt = new byte[Sodium.getPasswordHashSaltLength()];
        new SecureRandom().nextBytes(u.passwordSalt);
        u.passwordHashMemoryLimit = Sodium.PASSWORDHASH_MEMLIMIT_INTERACTIVE;
        u.passwordHashOperationsLimit = Sodium.PASSWORDHASH_OPSLIMIT_MODERATE;
        // We need a key with which to encrypt our data, so we'll use the hash of our password
        byte[] passwordHash = Sodium.createHashFromPassword(
                Sodium.getSymmetricKeyLength(),
                password.getBytes(),
                u.passwordSalt,
                u.passwordHashOperationsLimit,
                u.passwordHashMemoryLimit);
        prefs.setPasswordSalt(u.passwordSalt);

        byte[] symmetricKey = new byte[Sodium.getSymmetricKeyLength()];
        new SecureRandom().nextBytes(symmetricKey);
        prefs.setSymmetricKey(symmetricKey);

        EncryptedData wrappedSymmetricKey = Sodium.symmetricKeyEncrypt(symmetricKey, passwordHash);
        if (wrappedSymmetricKey == null) {
            return null;
        }
        u.wrappedSymmetricKey = wrappedSymmetricKey.cipherText;
        u.wrappedSymmetricKeyNonce = wrappedSymmetricKey.nonce;

        EncryptedData wrappedSecretKey = Sodium.symmetricKeyEncrypt(kp.secretKey, passwordHash);
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
                Prefs prefs = Prefs.get(this);
                prefs.setUserId(resp.id);
                prefs.setUsername(user.username);

                login(user.username, password);

//                App.runOnUiThread(new UiRunnable() {
//                    @Override
//                    public void run() {
//                        showMapActivity();
//                    }
//                });
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

    @UiThread
    private void showKeyboard(EditText field) {
        field.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    @UiThread
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.constraintLayout).getWindowToken(), 0);
    }
}
