package io.pijun.george.task;

import android.content.Context;
import android.util.Base64;

import java.util.Arrays;

import io.pijun.george.Hex;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.Sodium;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.SecretKeyEncryptedMessage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddFriendTask {

    private final Context mContext;
    private final String mUsername;
    private final OscarAPI mClient;

    public AddFriendTask(Context context, String username) {
        this.mContext = context;
        this.mUsername = username;
        mClient = OscarClient.newInstance(Prefs.get(context).getAccessToken());
    }

    public void begin() {
        mClient.searchForUser(mUsername).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful()) {
                    L.i("response: " + Base64.encodeToString(response.body().publicKey, Base64.NO_WRAP));
                    onReceivedUser(response.body());
                } else {
                    L.i("error getting pub id: " + OscarError.fromResponse(response));
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                L.i("serious problem getting pub id");
            }
        });
    }

    private void onReceivedUser(User user) {
        UserComm comm = UserComm.newOngoingLocationRequest("Please share your location with me.");
        SecretKeyEncryptedMessage msg = Sodium.publicKeyEncrypt(comm.toJSON(), user.publicKey, Prefs.get(mContext).getKeyPair().secretKey);
        L.i("sending message to " + Arrays.toString(user.id));
        mClient.sendMessage(Hex.toHexString(user.id), msg).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    L.i("successfully sent the message");
                } else {
                    L.i("failed to send message: " + OscarError.fromResponse(response));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                L.w("serious failure sending message", t);
            }
        });
    }

}
