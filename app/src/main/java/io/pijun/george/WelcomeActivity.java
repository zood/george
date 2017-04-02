package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
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
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.models.Snapshot;
import retrofit2.Response;

public class WelcomeActivity extends AppCompatActivity implements View.OnLayoutChangeListener {

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, WelcomeActivity.class);
    }

    private int mCurrentTask = WelcomeLayout.TASK_SPLASH;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_welcome);
        final WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
//        root.addOnLayoutChangeListener(this);

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

        final WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        if (root != null) {
            root.setCloudMovementEnabled(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        final WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        if (root != null) {
            root.setCloudMovementEnabled(false);
        }
    }

    @Override
    public void onBackPressed() {
        WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        int state = root.getState();
        if (state == WelcomeLayout.STATE_REGISTER || state == WelcomeLayout.STATE_SIGN_IN) {
            root.transitionTo(WelcomeLayout.STATE_SPLASH);
            return;
        }

        super.onBackPressed();
    }

    @UiThread
    public void onSignInAction(View v) {
        // If we're already in sign in mode, then attempt to login
//        if (mCurrentTask == WelcomeLayout.TASK_SIGN_IN) {
//            onLoginAction();
//            return;
//        }
//
//        mCurrentTask = WelcomeLayout.TASK_SIGN_IN;
//        WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
//        root.setTask(WelcomeLayout.TASK_SIGN_IN, true);
    }

    public void onShowRegistration(View v) {
        WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        root.transitionTo(WelcomeLayout.STATE_REGISTER);
    }

    @UiThread
    public void onRegisterAction(View v) {
        /*
        // If we're already in register mode, then attempt to create an account
        if (mCurrentTask == WelcomeLayout.TASK_REGISTER) {
            onCreateAccountAction();
            return;
        }

        mCurrentTask = WelcomeLayout.TASK_REGISTER;
        WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        root.setTask(WelcomeLayout.TASK_REGISTER, true);
        */
    }

    @UiThread
    public void onCreateAccountAction() {
        EditText usernameField = (EditText) findViewById(R.id.si_username);
        final String username = usernameField.getText().toString();
        if (TextUtils.isEmpty(username)) {
            Utils.showAlert(this, 0, R.string.need_username_msg);
            return;
        }

        EditText passwordField = (EditText) findViewById(R.id.si_password);
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
    public void onLoginAction() {
        EditText usernameField = (EditText) findViewById(R.id.si_username);
        final String username = usernameField.getText().toString();
        if (TextUtils.isEmpty(username)) {
            Utils.showAlert(this, 0, R.string.enter_username_msg);
        }

        EditText passwordField = (EditText) findViewById(R.id.si_password);
        final String password = passwordField.getText().toString();

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                login(username, password);
            }
        });
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
        imm.hideSoftInputFromWindow(findViewById(R.id.root).getWindowToken(), 0);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        WelcomeLayout root = (WelcomeLayout) v;
        root.setCloudMovementEnabled(true);
    }
}
