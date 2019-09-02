package xyz.zood.george;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

import io.pijun.george.App;
import io.pijun.george.CloudLogger;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.UiRunnable;
import io.pijun.george.Utils;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.SearchUserResult;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.service.BackupDatabaseJob;
import retrofit2.Response;
import xyz.zood.george.databinding.ActivityAddFriendBinding;

public class AddFriendActivity extends AppCompatActivity {

    private ActivityAddFriendBinding binding;
    private Drawable invalidUserIcon;
    private Drawable validUserIcon;

    public static Intent newIntent(@NonNull Context ctx) {
        return new Intent(ctx, AddFriendActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_friend);

        binding.username.addTextChangedListener(
                new UsernameWatcher(
                        binding.usernameContainer, binding.username));
        binding.usernameContainer.setEndIconVisible(true);

        int lightRed = getResources().getColor(R.color.zood_red_light);
        //noinspection ConstantConditions
        invalidUserIcon = getDrawable(R.drawable.ic_close_black_24dp).mutate();
        invalidUserIcon.setTint(lightRed);

        int green = getResources().getColor(R.color.zood_green);
        //noinspection ConstantConditions
        validUserIcon = getDrawable(R.drawable.ic_check_black_24dp).mutate();
        validUserIcon.setTint(green);
    }

    @Override
    protected void onDestroy() {
        binding = null;
        invalidUserIcon = null;
        validUserIcon = null;

        super.onDestroy();
    }

    @WorkerThread
    private void addFriend(@NonNull String username) {
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (accessToken == null || accessToken.equals("")) {
            addFriendFinished(getString(R.string.not_logged_in_access_token_msg));
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            addFriendFinished(getString(R.string.not_logged_in_key_pair_msg));
            return;
        }

        DB db = DB.get();
        UserRecord user = db.getUser(username);
        if (user == null) {
            // the user should already have been added by the 'search as you type' feature
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    addFriendFinished(getString(R.string.unknown_error_getting_user_info));
                }
            });
            return;
        }

        // check if we already have this user as a friend, and if we're already sharing with them
        final FriendRecord friend = db.getFriendByUserId(user.id);
        if (friend != null) {
            if (friend.sendingBoxId != null) {
                // send the sending box id to this person one more time, just in case
                UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                String errMsg = OscarClient.queueSendMessage(this, user, keyPair, accessToken, comm.toJSON(), false, false);
                if (errMsg != null) {
                    CloudLogger.log(errMsg);
                }
                addFriendFinished(null);
                return;
            }
        }

        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
        String errMsg = OscarClient.queueSendMessage(this, user, keyPair, accessToken, comm.toJSON(), false, false);
        if (errMsg != null) {
            addFriendFinished(getString(R.string.sharing_grant_failed_msg, errMsg));
            return;
        }

        try {
            db.startSharingWith(user, sendingBoxId);
        } catch (DB.DBException dbe) {
            addFriendFinished(getString(R.string.database_error_msg, dbe.getLocalizedMessage()));
            CloudLogger.log(dbe);
            return;
        }

        try {
            AvatarManager.sendAvatarToUsers(this, Collections.singletonList(user), keyPair, accessToken);
        } catch (IOException ex) {
            CloudLogger.log(ex);
            // We're purposely not returning early here. This isn't a critical error.
        }

        addFriendFinished(null);
        BackupDatabaseJob.scheduleBackup(this);
    }

    @WorkerThread
    private void checkUserValidity(@NonNull String username) {
        DB db = DB.get();
        UserRecord user = db.getUser(username);
        if (user != null) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    displayUserValidity(username, true);
                }
            });
            return;
        }

        // we have to check with the server
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (accessToken == null || accessToken.equals("")) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    Utils.showAlert(AddFriendActivity.this,
                            R.string.error,
                            R.string.not_logged_in_access_token_msg,
                            getSupportFragmentManager());
                }
            });
            return;
        }

        Response<SearchUserResult> response;
        try {
            response = OscarClient.newInstance(accessToken).searchForUser(username).execute();
        } catch (IOException ignore) {
            L.w("network error checking for user existence");
            return;
        }

        if (!response.isSuccessful()) {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    displayUserValidity(username, false);
                }
            });
            return;
        }

        SearchUserResult result = response.body();
        if (result == null) {
            L.w("Received a null result checking for user validity");
            return;
        }
        // Time for TOFU!
        /*
        Make sure the server gave us a response with the same username.
        I'm not sure what type of attack this defends against, but it
        seems like a good idea.
         */
        if (!username.equals(result.username)) {
            L.w("Received a SearchUserResult with a mismatched username. Have '"+username+"', but got '"+result.username+"'");
            return;
        }
        try {
            db.addUser(result.id, result.username, result.publicKey);
        } catch (DB.DBException ex) {
            L.e("Failed to add user '"+ username + "'", ex);
        }

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                displayUserValidity(username, true);
            }
        });
    }

    @UiThread
    private void displayUserValidity(@NonNull String username, boolean valid) {
        if (binding == null) {
            return;
        }

        // does the username match the current entry?
        Editable text = binding.username.getText();
        if (text == null) {
            // If there is no text, we shouldn't show anything
            binding.usernameContainer.setEndIconDrawable(null);
            binding.addFriend.setEnabled(false);
            return;
        }
        String currEntry = text.toString().trim().toLowerCase(Locale.US);
        if (!currEntry.equals(username)) {
            return;
        }

        if (valid) {
            binding.usernameContainer.setEndIconDrawable(validUserIcon);
            binding.addFriend.setEnabled(true);
        } else {
            binding.usernameContainer.setEndIconDrawable(invalidUserIcon);
            binding.addFriend.setEnabled(false);
        }
    }

    @AnyThread
    private void addFriendFinished(@Nullable String msg) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                // If no error, finish the activity
                if (msg == null) {
                    finish();
                    return;
                }

                // Show the error?
                AlertDialog.Builder bldr = new AlertDialog.Builder(AddFriendActivity.this);
                bldr.setTitle(R.string.error);
                bldr.setMessage(msg);
                bldr.setPositiveButton(R.string.ok, null);
                bldr.setCancelable(true);
                bldr.show();
            }
        });
    }

    @UiThread
    public void onAddFriendAction(View v) {
        Editable text = binding.username.getText();
        if (text == null) {
            throw new NullPointerException("How did we get into this state?");
        }
        String username = text.toString().trim().toLowerCase(Locale.US);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                addFriend(username);
            }
        });
    }

    @UiThread
    public void onBackClicked(View v) {
        finish();
    }

    private class UsernameWatcher implements TextWatcher {

        final TextInputLayout layout;
        final TextInputEditText editText;

        UsernameWatcher(@NonNull TextInputLayout layout, @NonNull TextInputEditText editText) {
            this.layout = layout;
            this.editText = editText;
        }

        @Override @UiThread
        public void afterTextChanged(Editable s) {
            if (s != null) {
                String username = s.toString().trim().toLowerCase(Locale.US);
                if (username.equals("")) {
                    binding.addFriend.setEnabled(false);
                    layout.setEndIconDrawable(null);
                    return;
                }

                if (Utils.isValidUsername(username)) {
                    layout.setEndIconDrawable(null);
                    binding.addFriend.setEnabled(false);
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            checkUserValidity(username);
                        }
                    });
                } else {
                    layout.setEndIconDrawable(invalidUserIcon);
                    binding.addFriend.setEnabled(false);
                }
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

    }

}
