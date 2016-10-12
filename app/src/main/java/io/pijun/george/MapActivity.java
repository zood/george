package io.pijun.george;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.EditText;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;

import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.SecretKeyEncryptedMessage;
import io.pijun.george.crypto.Vault;
import io.pijun.george.models.ShareRequest;
import io.pijun.george.task.AddFriendTask;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity {

    private EditText mAddUsernameField;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MapActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (Prefs.get(this).getAccessToken() == null) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
        }

        getWindow().getDecorView().setBackground(null);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_map);

        NavigationView navView = (NavigationView) findViewById(R.id.navigation);
        navView.setNavigationItemSelectedListener(navItemListener);
    }

    private void addFriendAction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.add_friend);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String username = mAddUsernameField.getText().toString();
                new AddFriendTask(MapActivity.this, username).begin();
            }
        });
        builder.setCancelable(true);
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mAddUsernameField = null;
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAddUsernameField = null;
            }
        });
        builder.setView(R.layout.alert_add_friend);
        final AlertDialog dialog = builder.create();
        dialog.show();
        mAddUsernameField = (EditText) dialog.findViewById(R.id.friend_username);
    }

    private void getMessagesAction() {
        OscarAPI client = OscarClient.newInstance(Prefs.get(this).getAccessToken());
        try {
            Response<Message[]> response = client.getMessages().execute();
            if (response.isSuccessful()) {
                byte[] secretKey = Prefs.get(MapActivity.this).getKeyPair().secretKey;
                Message[] messages = response.body();
                DB db = new DB(this);
                for (Message msg : messages) {
                    try {
                        byte[] pubKey = Vault.getPublicKey(MapActivity.this, msg.senderId);
                        byte[] msgBytes = Sodium.publicKeyDecrypt(msg.cipherText, msg.nonce, pubKey, secretKey);
                        UserComm comm = UserComm.fromJSON(msgBytes);
                        L.i("comm: " + comm);
                        Response<User> userResp = client.getUser(Hex.toHexString(msg.senderId)).execute();
                        String username = "";
                        if (userResp.isSuccessful()) {
                            username = userResp.body().username;
                        } else {
                            L.i("username is not successful");
                        }
                        long result = db.addShareRequest(username, msg.senderId, comm.note);
                        L.i("add request: " + result);
                    } catch (IOException ex) {
                        L.w("trouble receiving public key  or username for user " + Hex.toHexString(msg.senderId), ex);
                    }
                }
            }
        } catch (IOException ex) {
            L.w("serious problem getting messages", ex);
        }
    }

    private void showFriendRequests() {
        DB db = new DB(this);
        final ArrayList<ShareRequest> requests = db.getShareRequests();
        App.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MapActivity.this, R.style.AlertDialogTheme);
                builder.setTitle(R.string.friend_requests);
                CharSequence[] usernames = new CharSequence[requests.size()];
                boolean[] checked = new boolean[requests.size()];
                for (int i=0; i<requests.size(); i++) {
                    usernames[i] = requests.get(i).username;
                    checked[i] = false;
                }
                builder.setMultiChoiceItems(usernames, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        L.i("dialogchoice.onclick - which: " + which + " isChecked: " + isChecked);
                    }
                });
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        L.i("dialog ok");
                        App.runInBackground(new Runnable() {
                            @Override
                            public void run() {
                                approveAllRequests(requests);
                            }
                        });
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.setCancelable(true);
                builder.show();
            }
        });
    }

    @WorkerThread
    private void approveAllRequests(ArrayList<ShareRequest> requests) {
        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(boxId);
        DB db = new DB(this);
        OscarAPI client = OscarClient.newInstance(Prefs.get(this).getAccessToken());
        for (ShareRequest r : requests) {
            // add this to our database
            db.addGrantedShare(r.username, r.userId, boxId);
            // send a message letting the user know that we approved the request and the drop box they should check
            UserComm sharingGrant = UserComm.newLocationSharingGrant(boxId);
            byte[] rcvrPubKey;
            try {
                rcvrPubKey = Vault.getPublicKey(this, r.userId);
            } catch (IOException ex) {
                L.w("unable to get public key to approve sharing request", ex);
                continue;
            }
            SecretKeyEncryptedMessage message = Sodium.publicKeyEncrypt(
                    sharingGrant.toJSON(),
                    rcvrPubKey,
                    Prefs.get(this).getKeyPair().secretKey);
            try {
                Response<Void> response = client.sendMessage(Hex.toHexString(r.userId), message).execute();
                if (!response.isSuccessful()) {
                    L.i("sending sharing grant failed: " + OscarError.fromResponse(response));
                    continue;
                }
            } catch (Exception ex) {
                L.w("unable to send message approving sharing request", ex);
            }
        }

    }

    private NavigationView.OnNavigationItemSelectedListener navItemListener = new NavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            L.i("clicked item: " + item.getTitle());
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.closeDrawers();

            if (item.getItemId() == R.id.add_friend) {
                addFriendAction();
            } else if (item.getItemId() == R.id.check_messages) {
                App.runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        getMessagesAction();
                    }
                });
            } else if (item.getItemId() == R.id.friend_requests) {
                App.runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        showFriendRequests();
                    }
                });
            }
            return false;
        }
    };
}
