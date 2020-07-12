package io.pijun.george;

import android.content.Context;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.PicassoTools;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
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
import xyz.zood.george.receiver.PassiveLocationReceiver;
import xyz.zood.george.receiver.UserActivityReceiver;
import io.pijun.george.service.LocationJobService;
import io.pijun.george.sodium.HashConfig;
import retrofit2.Response;
import xyz.zood.george.AvatarManager;

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
        UnknownPasswordHashAlgorithm,
        UserNotFound,
        SymmetricKeyDecryptionFailed,
    }

    private final CopyOnWriteArrayList<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();
    private static AuthenticationManager singleton;

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
    void logIn(@NonNull Context ctx, @NonNull final String username, @NonNull final String password, @Nullable LoginWatcher watcher) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                _logIn(ctx, username, password, watcher);
            }
        });
    }

    @WorkerThread
    private void _logIn(@NonNull Context ctx, @NonNull final String username, @NonNull final String password, @Nullable LoginWatcher watcher) {
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

        HashConfig hashCfg = HashConfig.create(authChallenge.user.passwordHashAlgorithm,
                authChallenge.user.passwordHashOperationsLimit,
                authChallenge.user.passwordHashMemoryLimit);
        if (hashCfg == null) {
            notifyLoginWatchers(Error.UnknownPasswordHashAlgorithm, null, watcher);
            return;
        }
        final byte[] passwordHash = Sodium.stretchPassword(Sodium.getSymmetricKeyLength(),
                password.getBytes(Constants.utf8),
                authChallenge.user.passwordSalt,
                hashCfg.alg.sodiumId,
                hashCfg.getOpsLimit(),
                hashCfg.getMemLimit());
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

            if (snapshot.schemaVersion > DB.get().getSchemaVersion()) {
                notifyLoginWatchers(Error.OutdatedClient, null, watcher);
                return;
            }

            try {
                DB.get().restoreDatabase(ctx, snapshot);
            } catch (DB.DBException ex) {
                CloudLogger.log(ex);
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

        // If the device has an FCM token, upload it
        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult result) {
                OscarClient.queueAddFcmToken(ctx, loginResponse.accessToken, result.getToken());
            }
        });

        // request avatars from all friends
        UserComm avatarReq = UserComm.newAvatarRequest();
        byte[] reqJson = avatarReq.toJSON();
        ArrayList<FriendRecord> friends = DB.get().getFriends();
        for (FriendRecord f : friends) {
            String err = OscarClient.queueSendMessage(OscarClient.getQueue(ctx), f.user, kp, loginResponse.accessToken, reqJson, false, false);
            if (err != null) {
                L.w("Error queue avatar request to friend " + f.user.username + ": " + err);
            }
        }

        // schedule periodic location updates
        LocationJobService.scheduleLocationJobService(ctx);
        PassiveLocationReceiver.requestUpdates(ctx);
        UserActivityReceiver.requestUpdates(ctx);
    }

    @AnyThread
    public void logOut(@NonNull Context ctx, @Nullable LogoutWatcher completion) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                Prefs prefs = Prefs.get(ctx);
                String accessToken = prefs.getAccessToken();
                String fcmToken = prefs.getFcmToken();
                if (accessToken != null && fcmToken != null) {
                    OscarClient.queueDeleteFcmToken(ctx, accessToken, fcmToken);
                }
                prefs.clearAll();
                DB.get().deleteAllData();
                LocationJobService.cancelLocationJobService(ctx);
                PassiveLocationReceiver.stopUpdates(ctx);
                UserActivityReceiver.stopUpdates(ctx);
                AvatarManager.deleteAll(ctx);

                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        PicassoTools.clearCache(Picasso.with(App.getApp()));
                        if (completion != null) {
                            completion.onUserLoggedOut();
                        }
                        for (WeakReference<Listener> ref : listeners) {
                            Listener l = ref.get();
                            if (l != null) {
                                l.onUserLoggedOut();
                            }
                        }
                    }
                });
            }
        });
    }

    //region Listener management
    public void addListener(@NonNull Listener listener) {
        WeakReference<Listener> ref = new WeakReference<>(listener);
        listeners.add(ref);
    }

    @WorkerThread
    private void notifyLoginWatchers(@NonNull final Error err, @Nullable final String detail, @Nullable LoginWatcher watcher) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (watcher != null) {
                    watcher.onUserLoggedIn(err, detail);
                }

                for (WeakReference<Listener> ref : listeners) {
                    Listener l = ref.get();
                    if (l != null) {
                        l.onUserLoggedIn(err, detail);
                    }
                }
            }
        });
    }

    public void removeListener(@NonNull Listener listener) {
        int i=0;
        while (i<listeners.size()) {
            WeakReference<Listener> ref = listeners.get(i);
            Listener l = ref.get();
            if (l == null || l == listener) {
                listeners.remove(i);
                continue;
            }
            i++;
        }
    }

    public interface Listener extends LoginWatcher, LogoutWatcher {
        @UiThread default void onUserLoggedIn(@NonNull Error err, @Nullable String detail) {}
        @UiThread default void onUserLoggedOut() {}
    }

    public interface LoginWatcher {
        @UiThread
        void onUserLoggedIn(@NonNull Error err, @Nullable String detail);
    }

    public interface LogoutWatcher {
        @UiThread
        void onUserLoggedOut();
    }
    //endregion
}
