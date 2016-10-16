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
import io.pijun.george.crypto.SecretKeyEncryptedMessage;
import retrofit2.Response;

public class FriendsActivity extends AppCompatActivity {

    public static Intent newIntent(Context context) {
        return new Intent(context, FriendsActivity.class);
    }

    private EditText mUsernameField;
    private EditText mNoteField;
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
                final String note = mNoteField.getText().toString();
                final boolean share = mShareCheckbox.isChecked();
                App.runInBackground(new WorkerRunnable() {
                    @Override
                    public void run() {
                        onSendFriendRequest(username, note, share);
                    }
                });
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.show();

        mUsernameField = (EditText) dialog.findViewById(R.id.username);
        mNoteField = (EditText) dialog.findViewById(R.id.note);
        mShareCheckbox = (CheckBox) dialog.findViewById(R.id.share_location_checkbox);
    }

    @WorkerThread
    private void onSendFriendRequest(@NonNull String username, String note, boolean shareLocation) {
        Prefs prefs = Prefs.get(this);
        OscarAPI api = OscarClient.newInstance(prefs.getAccessToken());
        try {
            Response<User> searchResponse = api.searchForUser(username).execute();
            if (!searchResponse.isSuccessful()) {
                OscarError err = OscarError.fromResponse(searchResponse);
                Utils.showStringAlert(this, null, "Unable to find username: " + err);
                return;
            }
            User userToRequest = searchResponse.body();
            UserComm comm = UserComm.newLocationSharingRequest(note);
            SecretKeyEncryptedMessage msg = Sodium.publicKeyEncrypt(comm.toJSON(), userToRequest.publicKey, prefs.getKeyPair().secretKey);
            Response<Void> sendResponse = api.sendMessage(Hex.toHexString(userToRequest.id), msg).execute();
            if (!sendResponse.isSuccessful()) {
                OscarError err = OscarError.fromResponse(sendResponse);
                Utils.showStringAlert(this, null, "Unable to send message: " + err);
                return;
            }

            DB db = new DB(this);
            db.addFriendWithSharingRequest(username, userToRequest.id, userToRequest.publicKey);

            if (shareLocation) {
                byte[] dropBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
                new SecureRandom().nextBytes(dropBoxId);
                db.setSendingDropBoxId(username, dropBoxId);
            }
            Utils.showStringAlert(this, null, "User request added");
            mAdapter.reloadFriends(this);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Serious error setting up friend request: " + ex.getLocalizedMessage());
        } catch (DB.DBException re) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
        }
    }
}
