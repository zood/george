package io.pijun.george;

import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.LimitedShare;
import io.pijun.george.network.Network;
import io.pijun.george.service.ActivityTransitionHandler;
import io.pijun.george.service.LimitedShareService;
import retrofit2.Response;

public class LocationUtils {

    public static boolean upload(@NonNull Context ctx, @NonNull Location location, boolean immediately) {
        if (!Network.isConnected(ctx)) {
            return false;
        }

        Prefs prefs = Prefs.get(ctx);
        String token = prefs.getAccessToken();
        KeyPair keyPair = prefs.getKeyPair();
        if (token == null || keyPair == null) {
            L.i("LM.upload: token or keypair was null, so skipping upload");
            return false;
        }

        UserComm locMsg = UserComm.newLocationInfo(location, ActivityTransitionHandler.getCurrentMovement());
        byte[] msgBytes = locMsg.toJSON();
        // share to our friends
        ArrayList<FriendRecord> friends = DB.get(ctx).getFriendsToShareWith();
        HashMap<String, EncryptedData> pkgs = new HashMap<>(friends.size());
        for (FriendRecord f : friends) {
            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, f.user.publicKey, keyPair.secretKey);
            if (encMsg == null) {
                L.w("LM.upload encryption failed for " + f.user.username);
                continue;
            }
            pkgs.put(Hex.toHexString(f.sendingBoxId), encMsg);
        }
        LimitedShare ls = DB.get(ctx).getLimitedShare();
        if (ls != null) {
            L.i("LM.upload: to limited share");
            if (LimitedShareService.IsRunning) {
                EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, ls.publicKey, keyPair.secretKey);
                if (encMsg != null) {
                    pkgs.put(Hex.toHexString(ls.sendingBoxId), encMsg);
                } else {
                    L.w("LM.upload: limited share encryption failed");
                }
            } else {
                L.i("LM.upload: oops. the limited share isn't running. we'll delete it.");
                DB.get(ctx).deleteLimitedShares();
            }
        }
        if (pkgs.size() > 0) {
            if (immediately) {
                OscarAPI api = OscarClient.newInstance(token);
                try {
                    Response<Void> response = api.dropMultiplePackages(pkgs).execute();
                    if (response.isSuccessful()) {
                        L.i("LUtils successfully uploaded a location");
                        prefs.setLastLocationUpdateTime(System.currentTimeMillis());
                        return true;
                    }
                    OscarError err = OscarError.fromResponse(response);
                    L.w("LM.upload error dropping packages - " + err);
                    return false;
                } catch (IOException ex) {
                    L.w("Failed to upload location because " + ex.getLocalizedMessage());
                    return false;
                }
            } else {
                OscarClient.queueDropMultiplePackages(ctx, token, pkgs);
                prefs.setLastLocationUpdateTime(System.currentTimeMillis());
                L.i("LUtils successfully queued a location");
            }
        }

        return true;
    }

}
