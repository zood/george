package io.pijun.george;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.crashlytics.android.Crashlytics;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;

import io.pijun.george.api.AuthenticationChallenge;
import io.pijun.george.api.FinishedAuthenticationChallenge;
import io.pijun.george.api.LoginResponse;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.ServerPublicKeyResponse;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.Snapshot;
import io.pijun.george.receiver.PassiveLocationReceiver;
import io.pijun.george.receiver.UserActivityReceiver;
import io.pijun.george.service.FcmTokenRegistrar;
import io.pijun.george.service.LocationJobService;
import retrofit2.Response;

public class AuthenticationManager {

    public enum Error {
        AuthenticationChallengeCreationFailed,
        AuthenticationChallengeFailed,
        DatabaseBackupDecryptionFailed,
        DatabaseBackupParsingFailed,
        DatabaseRestoreFailed,
        IncorrectPassword,
        MalformedAuthenticationChallengeResponse,
        MalformedDatabaseBackupResponse,
        MalformedLoginResponse,
        MalformedServerKeyResponse,
        NetworkErrorCompletingAuthenticationChallenge,
        NetworkErrorRetrievingDatabaseBackup,
        NetworkErrorRetrievingServerKey,
        None,
        NullPasswordHash,
        OutdatedClient,
        Unknown,
        UnknownErrorRetrievingDatabaseBackup,
        UnknownErrorRetrievingServerKey,
        UserNotFound,
        SymmetricKeyDecryptionFailed,
    }

    private HashSet<WeakReference<Listener>> listeners = new HashSet<>();
    private static AuthenticationManager singleton;

    public void addListener(@NonNull Listener l) {
        WeakReference<Listener> ref = new WeakReference<>(l);
        synchronized (AuthenticationManager.class) {
            listeners.add(ref);
        }
    }

    public static AuthenticationManager get() {
        if (singleton == null) {
            synchronized (AuthenticationManager.class) {
                if (singleton == null) {
                    singleton = new AuthenticationManager();
                }
            }
        }
        return singleton;
    }

    @AnyThread @CheckResult
    public static boolean isLoggedIn(@NonNull Context ctx) {
        Prefs prefs = Prefs.get(ctx);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        byte[] passwordSalt = prefs.getPasswordSalt();
        byte[] symmetricKey = prefs.getSymmetricKey();
        byte[] userId = prefs.getUserId();

        //noinspection RedundantIfStatement
        if (token != null && keyPair != null && passwordSalt != null && symmetricKey != null && userId != null) {
            return true;
        }

        return false;
    }

    @AnyThread
    public void logIn(@NonNull Context ctx, @NonNull final String username, @NonNull final String password, @Nullable LoginWatcher watcher) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                _logIn(ctx, username, password, watcher);
            }
        });
    }

    @WorkerThread
    private void _logIn(@NonNull Context ctx, @NonNull final String username, @NonNull final String password, @Nullable LoginWatcher watcher) {
        L.i("logging in");
        OscarAPI api = OscarClient.newInstance(null);
        Response<AuthenticationChallenge> startChallengeResp;
        try {
            startChallengeResp = api.getAuthenticationChallenge(username).execute();
        } catch (IOException ex) {
            notifyLoginWatchers(Error.AuthenticationChallengeCreationFailed, ex.getLocalizedMessage(), watcher);
            return;
        }

        if (!startChallengeResp.isSuccessful()) {
            OscarError err = OscarError.fromResponse(startChallengeResp);
            if (err != null && err.code == OscarError.ERROR_USER_NOT_FOUND) {
                notifyLoginWatchers(Error.UserNotFound, null, watcher);
            } else {
                notifyLoginWatchers(Error.Unknown, null, watcher);
            }
            return;
        }
        AuthenticationChallenge authChallenge = startChallengeResp.body();
        if (authChallenge == null) {
            notifyLoginWatchers(Error.MalformedAuthenticationChallengeResponse, null, watcher);
            return;
        }
        final byte[] passwordHash = Sodium.createHashFromPassword(
                Sodium.getSymmetricKeyLength(),
                password.getBytes(),
                authChallenge.user.passwordSalt,
                authChallenge.user.passwordHashOperationsLimit,
                authChallenge.user.passwordHashMemoryLimit);
        if (passwordHash == null) {
            notifyLoginWatchers(Error.NullPasswordHash, null, watcher);
            return;
        }

        // now try to decrypt the private key
        final byte[] secretKey = Sodium.symmetricKeyDecrypt(
                authChallenge.user.wrappedSecretKey,
                authChallenge.user.wrappedSecretKeyNonce,
                passwordHash);
        if (secretKey == null) {
            notifyLoginWatchers(Error.IncorrectPassword, null, watcher);
            return;
        }

        // grab the server's public key
        Response<ServerPublicKeyResponse> pubKeyResp;
        try {
            pubKeyResp = api.getServerPublicKey().execute();
        } catch (IOException ex) {
            notifyLoginWatchers(Error.NetworkErrorRetrievingServerKey, null, watcher);
            return;
        }
        if (!pubKeyResp.isSuccessful()) {
            notifyLoginWatchers(Error.UnknownErrorRetrievingServerKey, null, watcher);
            return;
        }
        ServerPublicKeyResponse srvrPubKeyResp = pubKeyResp.body();
        if (srvrPubKeyResp == null) {
            notifyLoginWatchers(Error.MalformedServerKeyResponse, null, watcher);
            return;
        }
        byte[] pubKey = srvrPubKeyResp.publicKey;

        FinishedAuthenticationChallenge finishedChallenge = new FinishedAuthenticationChallenge();
        finishedChallenge.challenge = Sodium.publicKeyEncrypt(authChallenge.challenge, pubKey, secretKey);
        finishedChallenge.creationDate = Sodium.publicKeyEncrypt(authChallenge.creationDate, pubKey, secretKey);

        Response<LoginResponse> completeChallengeResp;
        try {
            completeChallengeResp = api.completeAuthenticationChallenge(username, finishedChallenge).execute();
        } catch (IOException ex) {
            notifyLoginWatchers(Error.NetworkErrorCompletingAuthenticationChallenge, null, watcher);
            return;
        }
        if (!completeChallengeResp.isSuccessful()) {
            OscarError err = OscarError.fromResponse(completeChallengeResp);
            String errMsg = null;
            if (err != null) {
                errMsg = err.toString();
            }
            notifyLoginWatchers(Error.AuthenticationChallengeFailed, errMsg, watcher);
            return;
        }

        final LoginResponse loginResponse = completeChallengeResp.body();
        if (loginResponse == null) {
            notifyLoginWatchers(Error.MalformedLoginResponse, null, watcher);
            return;
        }
        byte[] symmetricKey = Sodium.symmetricKeyDecrypt(loginResponse.wrappedSymmetricKey,
                loginResponse.wrappedSymmetricKeyNonce,
                passwordHash);
        if (symmetricKey == null) {
            notifyLoginWatchers(Error.SymmetricKeyDecryptionFailed, null, watcher);
            return;
        }

        // retrieve and restore the database backup
        api = OscarClient.newInstance(loginResponse.accessToken);
        Response<EncryptedData> dbResponse;
        try {
            dbResponse = api.getDatabaseBackup().execute();
        } catch (IOException ex) {
            notifyLoginWatchers(Error.NetworkErrorRetrievingDatabaseBackup, null, watcher);
            return;
        }
        if (!dbResponse.isSuccessful()) {
            OscarError err = OscarError.fromResponse(dbResponse);
            if (err == null || err.code != OscarError.ERROR_BACKUP_NOT_FOUND) {
                notifyLoginWatchers(Error.UnknownErrorRetrievingDatabaseBackup, null, watcher);
                return;
            }
            // If we get here, that means no backup was found. It's weird, but not strictly wrong.
        } else {
            EncryptedData encSnapshot = dbResponse.body();
            if (encSnapshot == null) {
                notifyLoginWatchers(Error.MalformedDatabaseBackupResponse, null, watcher);
                return;
            }
            byte[] jsonDb = Sodium.symmetricKeyDecrypt(encSnapshot.cipherText, encSnapshot.nonce, symmetricKey);
            if (jsonDb == null) {
                notifyLoginWatchers(Error.DatabaseBackupDecryptionFailed, null, watcher);
                return;
            }
            Snapshot snapshot = Snapshot.fromJson(jsonDb);
            // restore the database
            if (snapshot == null) {
                notifyLoginWatchers(Error.DatabaseBackupParsingFailed, null, watcher);
                return;
            }

            if (snapshot.schemaVersion > DB.get(ctx).getSchemaVersion()) {
                notifyLoginWatchers(Error.OutdatedClient, null, watcher);
                return;
            }

            try {
                DB.get(ctx).restoreDatabase(ctx, snapshot);
            } catch (DB.DBException ex) {
                Crashlytics.logException(ex);
                notifyLoginWatchers(Error.DatabaseRestoreFailed, null, watcher);
                return;
            }
        }

        Prefs prefs = Prefs.get(ctx);
        prefs.setSymmetricKey(symmetricKey);
        prefs.setPasswordSalt(authChallenge.user.passwordSalt);
        KeyPair kp = new KeyPair();
        kp.publicKey = authChallenge.user.publicKey;
        kp.secretKey = secretKey;
        prefs.setKeyPair(kp);
        prefs.setUserId(loginResponse.id);
        prefs.setUsername(username);
        prefs.setAccessToken(loginResponse.accessToken);

        notifyLoginWatchers(Error.None, null, watcher);

        // request avatars from all friends
        UserComm avatarReq = UserComm.newAvatarRequest();
        byte[] reqJson = avatarReq.toJSON();
        ArrayList<FriendRecord> friends = DB.get(ctx).getFriends();
        for (FriendRecord f : friends) {
            String err = OscarClient.queueSendMessage(ctx, f.user, reqJson, false, false);
            if (err != null) {
                L.w("Error queue avatar request to friend " + f.user.username + ": " + err);
            }
        }

        // schedule periodic location updates
        LocationJobService.scheduleLocationJobService(ctx);
        PassiveLocationReceiver.register(ctx);
        UserActivityReceiver.requestUpdates(ctx);
    }

    @AnyThread
    public void logOut(@NonNull Context ctx, @Nullable LogoutWatcher completion) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                String fcmToken = Prefs.get(ctx).getFcmToken();
                ctx.startService(FcmTokenRegistrar.newIntent(ctx, true, fcmToken));
                Prefs.get(ctx).clearAll();
                DB.get(ctx).deleteUserData();
                LocationJobService.cancelLocationJobService(ctx);
                PassiveLocationReceiver.unregister(ctx);
                UserActivityReceiver.stopUpdates(ctx);

                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        if (completion != null) {
                            completion.onUserLoggedOut();
                        }
                        synchronized (AuthenticationManager.class) {
                            for (WeakReference<Listener> ref : listeners) {
                                Listener l = ref.get();
                                if (l != null) {
                                    l.onUserLoggedOut();
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    @WorkerThread
    private void notifyLoginWatchers(@NonNull final Error err, @Nullable final String detail, @Nullable LoginWatcher watcher) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (watcher != null) {
                    watcher.onUserLoggedIn(err, detail);
                }

                synchronized (AuthenticationManager.class) {
                    for (WeakReference<Listener> ref : listeners) {
                        Listener l = ref.get();
                        if (l != null) {
                            l.onUserLoggedIn(err, detail);
                        }
                    }
                }
            }
        });
    }

    public void remove(@NonNull Listener l) {
        WeakReference<Listener> ref = new WeakReference<>(l);
        synchronized (AuthenticationManager.class) {
            listeners.remove(ref);
        }
    }

    interface Listener extends LoginWatcher, LogoutWatcher {
        default void onUserLoggedIn(@NonNull Error err, @Nullable String detail) {}
        default void onUserLoggedOut() {}
    }

    public interface LoginWatcher {
        @UiThread
        void onUserLoggedIn(@NonNull Error err, @Nullable String detail);
    }

    public interface LogoutWatcher {
        @UiThread
        void onUserLoggedOut();
    }
}
