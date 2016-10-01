package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import java.io.IOException;
import java.security.SecureRandom;

import io.pijun.george.api.AuthenticationChallenge;
import io.pijun.george.api.LoginResponse;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.crypto.SecretKeyEncryptedMessage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, LoginActivity.class);
    }
    private OscarAPI mApi;
    private AuthenticationChallenge mAuthChallenge;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        mApi = OscarClient.newInstance();

        L.i("symKeyLength: " + Sodium.getSymmetricKeyLength());
    }

    public void onSignUpClicked(View v) {
        EditText usernameField = (EditText) findViewById(R.id.username);
        final String username = usernameField.getText().toString();
        if (username.length() < 5) {
            Utils.showAlert(this, 0, R.string.username_too_short_msg);
            return;
        }

        EditText passwordField = (EditText) findViewById(R.id.password);
        EditText confirmedPasswordField = (EditText) findViewById(R.id.password_verified);
        final String password = passwordField.getText().toString();
        String confirmedPassword = confirmedPasswordField.getText().toString();
        L.i("about to check passwords");
        if (password.length() < 8) {
            Utils.showAlert(this, 0, R.string.password_too_short_msg);
            return;
        }
        if (!password.equals(confirmedPassword)) {
            Utils.showAlert(this, 0, R.string.password_mismatch_msg);
            return;
        }

        App.runInBackground(new Runnable() {
            @Override
            public void run() {
                final User user = generateUser(username, password);
                if (user == null) {
                    Utils.showAlert(LoginActivity.this, 0, R.string.unknown_user_generation_error_msg);
                    return;
                }
                App.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        registerUser(user);
                    }
                });
            }
        });
    }

    public void onLoginClicked(View v) {
        // get a challenge from the server
        L.i("onLoginClicked");
        EditText usernameField = (EditText) findViewById(R.id.username_login);
        final String username = usernameField.getText().toString();
        L.i("username: " + username);
        if (username.length() < 5) {
            Utils.showAlert(this, 0, R.string.username_too_short_msg);
            return;
        }
        EditText passwordField = (EditText) findViewById(R.id.password_login);
        final String password = passwordField.getText().toString();
        obtainAuthenticationChallenge(username, password);
    }

    public void obtainAuthenticationChallenge(final String username, final String password) {
        L.i("obtainAuthenticationChallenge");
        if (mAuthChallenge != null && mAuthChallenge.user.username.equalsIgnoreCase(username)) {
            processChallenge(username, password);
            return;
        }
        mApi.getAuthenticationChallenge(username).enqueue(new Callback<AuthenticationChallenge>() {
            @Override
            public void onResponse(Call<AuthenticationChallenge> call, Response<AuthenticationChallenge> response) {
                if (response.isSuccessful()) {
                    mAuthChallenge = response.body();
                    processChallenge(username, password);
                } else {
                    OscarError err = OscarError.fromReader(response.errorBody().charStream());
                    if (err.code == OscarError.ERROR_USER_NOT_FOUND) {
                        Utils.showStringAlert(LoginActivity.this, null, "Unknown user");
                    } else {
                        Utils.showStringAlert(LoginActivity.this, null, "Unknown error ");
                    }
                }
            }

            @Override
            public void onFailure(Call<AuthenticationChallenge> call, Throwable t) {
                L.i("serious error obtaining authentication challenge");
                Utils.showStringAlert(
                        LoginActivity.this,
                        null,
                        "serious error obtaining authentication challenge: " + t.getLocalizedMessage());
            }
        });
    }

    private void processChallenge(String username, String password) {
        L.i("processChallenge");
        final byte[] passwordHash = Sodium.createHashFromPassword(
                Sodium.getSymmetricKeyLength(),
                password.getBytes(),
                mAuthChallenge.user.passwordSalt,
                mAuthChallenge.user.passwordHashOperationsLimit,
                mAuthChallenge.user.passwordHashMemoryLimit);

        // now try to decrypt the private key
        final byte[] secretKey = Sodium.symmetricKeyDecrypt(
                mAuthChallenge.user.wrappedSecretKey,
                mAuthChallenge.user.wrappedSecretKeyNonce,
                passwordHash);
        if (secretKey == null) {
            L.i("Unable to decrypt the secret key");
        } else {
            L.i("secret key is " + secretKey.length + " bytes long");
        }

        SecretKeyEncryptedMessage encChallenge = Sodium.publicKeyEncrypt(mAuthChallenge.challenge, mAuthChallenge.publicKey, secretKey);
        mApi.completeAuthenticationChallenge(username, encChallenge).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful()) {
                    LoginResponse loginResponse = response.body();
                    L.i("login success: " + loginResponse);
                    // decrypt the symmetric key, and save it along with all the other user data
                    byte[] symmetricKey = Sodium.symmetricKeyDecrypt(loginResponse.wrappedSymmetricKey,
                            loginResponse.wrappedSymmetricKeyNonce,
                            passwordHash);
                    Prefs prefs = Prefs.get(LoginActivity.this);
                    prefs.setSymmetricKey(symmetricKey);
                    prefs.setUserId(loginResponse.id);
                    prefs.setAccessToken(loginResponse.accessToken);
                    prefs.setPasswordSalt(mAuthChallenge.user.passwordSalt);
                    KeyPair kp = new KeyPair();
                    kp.publicKey = mAuthChallenge.user.publicKey;
                    kp.secretKey = secretKey;
                    prefs.setKeyPair(kp);
                } else {
                    L.i("login failed: " + OscarError.fromReader(response.errorBody().charStream()));
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                L.i("serious failure logging in");
            }
        });
    }

    private void registerUser(User user) {
        L.i("creating new user");
        mApi.createUser(user).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                L.i("onResponse");
                if (response.isSuccessful()) {
                    onUserCreationSuccess(response.body());
                } else {
                    OscarError err = OscarError.fromReader(response.errorBody().charStream());
                    if (err != null) {
                        Utils.showStringAlert(LoginActivity.this, null, err.message);
                    }
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                L.i(t.getLocalizedMessage());
                Utils.showStringAlert(LoginActivity.this, null, "Serious error creating account");
            }
        });
    }

    @WorkerThread
    private User generateUser(String username, String password) {
        L.i("generateUser");
        User u = new User();
        u.username = username;
        KeyPair kp = new KeyPair();
        int result = Sodium.generateKeyPair(kp);
        if (result != 0) {
            Utils.showAlert(this, 0, R.string.keypair_generation_error);
            return null;
        }
        Prefs.get(this).setKeyPair(kp);
        u.publicKey = kp.publicKey;

        u.passwordSalt = new byte[Sodium.getPasswordHashSaltLength()];
        new SecureRandom().nextBytes(u.passwordSalt);
        u.passwordHashMemoryLimit = Sodium.PASSWORDHASH_MEMLIMIT_INTERACTIVE;
        u.passwordHashOperationsLimit = Sodium.PASSWORDHASH_OPSLIMIT_MODERATE;
        long now = System.currentTimeMillis();
        // We need a key with which to encrypt our data, so we'll use the hash of our password
        byte[] passwordHash = Sodium.createHashFromPassword(
                Sodium.getSymmetricKeyLength(),
                password.getBytes(),
                u.passwordSalt,
                u.passwordHashOperationsLimit,
                u.passwordHashMemoryLimit);
        L.i("took: " + (System.currentTimeMillis() - now));
        Prefs.get(this).setPasswordSalt(u.passwordSalt);

        byte[] symmetricKey = new byte[Sodium.getSymmetricKeyLength()];
        new SecureRandom().nextBytes(symmetricKey);
        Prefs.get(this).setSymmetricKey(symmetricKey);

        SecretKeyEncryptedMessage wrappedSymmetricKey = Sodium.symmetricKeyEncrypt(symmetricKey, passwordHash);
        if (wrappedSymmetricKey == null) {
            return null;
        }
        u.wrappedSymmetricKey = wrappedSymmetricKey.cipherText;
        u.wrappedSymmetricKeyNonce = wrappedSymmetricKey.nonce;

        SecretKeyEncryptedMessage wrappedSecretKey = Sodium.symmetricKeyEncrypt(kp.secretKey, passwordHash);
        u.wrappedSecretKey = wrappedSecretKey.cipherText;
        u.wrappedSecretKeyNonce = wrappedSecretKey.nonce;

        return u;
    }

    private void onUserCreationSuccess(LoginResponse lr) {
        L.i("user account created successfully");
        Prefs.get(this).setUserId(lr.id);
        Prefs.get(this).setAccessToken(lr.accessToken);
    }
}
