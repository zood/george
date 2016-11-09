package io.pijun.george;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import java.io.IOException;
import java.security.SecureRandom;

import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.crypto.PKEncryptedMessage;
import io.pijun.george.models.UserRecord;
import retrofit2.Response;

public class FriendsActivity extends AppCompatActivity implements FriendsAdapter.FriendsAdapterListener {

    public static Intent newIntent(Context context) {
        return new Intent(context, FriendsActivity.class);
    }

    private EditText mUsernameField;
    private CheckBox mShareCheckbox;
    private FriendsAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_friends);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.your_friends);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        RecyclerView list = (RecyclerView) findViewById(R.id.friends_list);
        mAdapter = new FriendsAdapter(this);
        mAdapter.setListener(this);
        list.setAdapter(mAdapter);
    }

    @UiThread
    public void onFABAction(View v) {
        L.i("onFABAction");
//        Snackbar.make(findViewById(R.id.coordinator), "Fabulous!", Snackbar.LENGTH_SHORT).show();
        onAddFriend();
    }

    @UiThread
    private void onAddFriend() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.friend_request);
        builder.setView(R.layout.friend_request_form);
        builder.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String username = mUsernameField.getText().toString();
                final boolean share = mShareCheckbox.isChecked();
                App.runInBackground(new WorkerRunnable() {
                    @Override
                    public void run() {
                        onSendFriendRequest(username, share);
                    }
                });
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.show();

        mUsernameField = (EditText) dialog.findViewById(R.id.username);
        mShareCheckbox = (CheckBox) dialog.findViewById(R.id.share_location_checkbox);
    }

    @WorkerThread
    private void onSendFriendRequest(@NonNull String username, boolean shareLocation) {
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(this, null, "How are you not logged in right now? (missing access token)");
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            Utils.showStringAlert(this, null, "How are you not logged in right now? (missing key pair)");
            return;
        }
        OscarAPI api = OscarClient.newInstance(prefs.getAccessToken());
        try {
            // check if we hae this person in our db. If not, retrieve their data and add it.
            DB db = DB.get(this);
            UserRecord userRecord = db.getUser(username);
            if (userRecord == null) {
                Response<User> searchResponse = api.searchForUser(username).execute();
                if (!searchResponse.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(searchResponse);
                    Utils.showStringAlert(this, null, "Unable to find username: " + err);
                    return;
                }
                User userToRequest = searchResponse.body();
                userRecord = db.addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey);
            }

            // check if we have this person in our database already. If not, add them.
            // add this user to our database (because TOFU)
//            try {
//
//                db.getUser(userToRequest.)
//                DB.get(this).addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey);
//            } catch (DB.DBException ex) {
//                Utils.showStringAlert(this, null, "Unexpected error adding user");
//                L.w("failed to add user on tofu", ex);
//            }

            UserComm comm = UserComm.newLocationSharingRequest();
            PKEncryptedMessage msg = Sodium.publicKeyEncrypt(comm.toJSON(), userRecord.publicKey, keyPair.secretKey);
            Response<Void> sendResponse = api.sendMessage(Hex.toHexString(userRecord.userId), msg).execute();
            if (!sendResponse.isSuccessful()) {
                OscarError err = OscarError.fromResponse(sendResponse);
                Utils.showStringAlert(this, null, "Unable to send request message: " + err);
                return;
            }

            byte[] sendingBoxId = null;
            if (shareLocation) {
                sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
                new SecureRandom().nextBytes(sendingBoxId);
                comm = UserComm.newLocationSharingGrant(sendingBoxId);
                msg = Sodium.publicKeyEncrypt(comm.toJSON(), userRecord.publicKey, keyPair.secretKey);
                sendResponse = api.sendMessage(Hex.toHexString(userRecord.userId), msg).execute();
                if (!sendResponse.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(sendResponse);
                    Utils.showStringAlert(this, null, "Unable to send grand message: " + err);
                    return;
                }
            }

            db.addFriend(userRecord.id, sendingBoxId, null);
            db.addOutgoingRequest(userRecord.id, System.currentTimeMillis());

            Utils.showStringAlert(this, null, "User request added");
            mAdapter.reloadFriends(this);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Serious error setting up friend request: " + ex.getLocalizedMessage());
        } catch (DB.DBException re) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
        }
    }

    @WorkerThread
    private void approveFriendRequest(long userId) {
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(this, null, "Your access token is missing");
            return;
        }
        KeyPair kp = Prefs.get(this).getKeyPair();
        if (kp == null) {
            Utils.showStringAlert(this, null, "Your key pair is missing");
            return;
        }
        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(boxId);
        UserComm comm = UserComm.newLocationSharingGrant(boxId);
        byte[] msgBytes = comm.toJSON();
        UserRecord user = DB.get(this).getUserById(userId);
        if (user == null) {
            throw new RuntimeException("How was approve called for an unknown user?");
        }
        PKEncryptedMessage encMsg = Sodium.publicKeyEncrypt(msgBytes, user.publicKey, kp.secretKey);
        OscarAPI client = OscarClient.newInstance(accessToken);
        try {
            Response<Void> response = client.sendMessage(Hex.toHexString(user.userId), encMsg).execute();
            if (!response.isSuccessful()) {
                Utils.showStringAlert(this, null, "Problem sending request approval");
                return;
            }
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Serious problem sending request approval");
            L.w("Serious problem sending request approval", ex);
            return;
        }

        try {
            DB.get(this).grantSharingTo(user.userId, boxId);
        } catch (DB.DBException ex) {
            Utils.showStringAlert(this, null, "Serious problem setting drop box id");
            L.w("serious problem setting drop box id", ex);
        }

        mAdapter.reloadFriends(this);
    }

    @WorkerThread
    private void rejectFriendRequest(long userId) {
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(this, null, "Your access token is missing");
            return;
        }
        KeyPair kp = Prefs.get(this).getKeyPair();
        if (kp == null) {
            Utils.showStringAlert(this, null, "Your key pair is missing");
            return;
        }
        UserComm comm = UserComm.newLocationSharingRejection();
        byte[] msgBytes = comm.toJSON();
        UserRecord user = DB.get(this).getUserById(userId);
        if (user == null) {
            L.w("friend with user id " + userId + " doesn't exist");
            return;
        }
        PKEncryptedMessage encMsg = Sodium.publicKeyEncrypt(msgBytes, user.publicKey, kp.secretKey);
        OscarAPI client = OscarClient.newInstance(accessToken);
        try {
            Response<Void> response = client.sendMessage(Hex.toHexString(user.userId), encMsg).execute();
            if (!response.isSuccessful()) {
                Utils.showStringAlert(this, null, "Problem sending rejection");
                return;
            }
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Serious problem sending rejection");
            L.w("Serious problem sending rejection", ex);
            return;
        }

        try {
            DB.get(this).rejectRequest(user);
        } catch (DB.DBException ex) {
            Utils.showStringAlert(this, null, "serious problem recording rejection");
            L.w("serious problem recording rejection", ex);
        }

        mAdapter.reloadFriends(this);
    }

    @Override
    @UiThread
    public void onApproveFriendRequest(final long userId) {
        L.i("onapprove " + userId);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                approveFriendRequest(userId);
            }
        });
    }

    @Override
    @UiThread
    public void onRejectFriendRequest(final long userId) {
        L.i("onreject: " + userId);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                rejectFriendRequest(userId);
            }
        });
    }
}
