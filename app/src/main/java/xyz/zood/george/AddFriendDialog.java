package xyz.zood.george;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddFriendDialog extends DialogFragment {

    private TextInputLayout usernameLayout;
    private TextInputEditText usernameEditText;
    private MaterialButton addButton;
    private ProgressBar progressBar;
    private String accessToken;
    private Drawable checkmark;

    public static AddFriendDialog newInstance() {
        return new AddFriendDialog();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        accessToken = Prefs.get(requireContext()).getAccessToken();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_friend, container, false);
        usernameLayout = view.findViewById(R.id.username_layout);
        usernameEditText = view.findViewById(R.id.username);
        addButton = view.findViewById(R.id.add);
        addButton.setEnabled(false);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddFriendAction();
            }
        });
        usernameEditText.addTextChangedListener(usernameWatcher);
        progressBar = view.findViewById(R.id.progress_bar);

        checkmark = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_black_24dp);
        if (checkmark != null) {
            checkmark = checkmark.mutate();
            checkmark.setTint(ContextCompat.getColor(requireContext(), R.color.zood_blue));
        }

        // give the window rounded corners
        Window win = getDialog().getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(R.drawable.rounded_background);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        usernameEditText = null;
        usernameLayout = null;
        addButton = null;
        progressBar = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Because we used a custom background on onCreateView, we need to re-apply the layout size
        // This doesn't work if we do it in onCreateView
        Window win = getDialog().getWindow();
        if (win != null) {
            win.setLayout((int)getResources().getDimension(R.dimen.dialog_width), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    //region Business logic

    @WorkerThread
    private void addFriend(@NonNull String username) {
        Prefs prefs = Prefs.get(requireContext());
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
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
            addFriendFinished(getString(R.string.unknown_error_getting_user_info));
            return;
        }

        // check if we already have this user as a friend, and if we're already sharing with them
        final FriendRecord friend = db.getFriendByUserId(user.id);
        if (friend != null) {
            if (friend.sendingBoxId != null) {
                // send the sending box id to this person one more time, just in case
                UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                String errMsg = OscarClient.queueSendMessage(requireContext(), user, keyPair, accessToken, comm.toJSON(), false, false);
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
        String errMsg = OscarClient.queueSendMessage(requireContext(), user, keyPair, accessToken, comm.toJSON(), false, false);
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
            AvatarManager.sendAvatarToUsers(requireContext(), Collections.singletonList(user), keyPair, accessToken);
        } catch (IOException ex) {
            CloudLogger.log(ex);
            // We're purposely not returning early here. This isn't a critical error.
        }

        addFriendFinished(null);
        BackupDatabaseJob.scheduleBackup(requireContext());
    }

    @AnyThread
    private void addFriendFinished(@Nullable String errMsg) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (errMsg == null) {
                    dismiss();
                    return;
                }

                usernameLayout.setError(errMsg);
                getDialog().setCancelable(true);
                progressBar.setVisibility(View.INVISIBLE);
                addButton.setVisibility(View.VISIBLE);
                usernameEditText.setEnabled(true);
                usernameEditText.setCompoundDrawables(null, null, null, null);
            }
        });
    }

    @UiThread
    private void onAddFriendAction() {
        Editable text = usernameEditText.getText();
        if (text == null) {
            return; // never happens
        }
        String username = text.toString().trim().toLowerCase(Locale.US);
        L.i("adding '"+username+"'");
        getDialog().setCancelable(false);
        progressBar.setVisibility(View.VISIBLE);
        addButton.setVisibility(View.INVISIBLE);
        usernameEditText.setEnabled(false);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                addFriend(username);
            }
        });
    }


    @AnyThread
    private void validateUsername(@NonNull final String username) {
        OscarClient.newInstance(accessToken).searchForUser(username).enqueue(new Callback<SearchUserResult>() {
            @Override
            public void onResponse(@NonNull Call<SearchUserResult> call, @NonNull Response<SearchUserResult> response) {
                // check if the UI is still loaded
                if (usernameEditText == null) {
                    return;
                }
                // check if we're still displaying the same username
                Editable text = usernameEditText.getText();
                if (text == null) {
                    // should never happen
                    return;
                }
                String currEntry = text.toString().trim().toLowerCase(Locale.US);
                if (!currEntry.equals(username)) {
                    return;
                }
                if (response.isSuccessful()) {
                    usernameEditText.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, checkmark, null);
                    addButton.setEnabled(true);
                    // add the user to our local database if not already there (TOFU!)
                    SearchUserResult result = response.body();
                    if (result != null) {
                        App.runInBackground(new WorkerRunnable() {
                            @Override
                            public void run() {
                                try {
                                    UserRecord user = DB.get().getUser(result.username);
                                    if (user == null) {
                                        L.i("No user record for " + username + ". Adding it.");
                                        // Make sure the server gave us a response with the same username.
                                        // I'm not sure what type of attack this defends against, but it
                                        // seems like a good idea.
                                        if (!username.equals(result.username)) {
                                            L.w("Received a SearchUserResult for '" + username + "' that contained the username '"+result.username+"'");
                                            return;
                                        }
                                        DB.get().addUser(result.id, result.username, result.publicKey);
                                        L.i("Successfully added " + result.username + "'s info into db");
                                    }
                                } catch (DB.DBException ex) {
                                    L.w("unable to add user", ex);
                                }
                            }
                        });
                    }
                } else {
                    usernameLayout.setError(getString(R.string.not_a_user));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SearchUserResult> call, @NonNull Throwable t) {

            }
        });
    }

    //endregion

    //region TextWatcher

    private final TextWatcher usernameWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            usernameEditText.setCompoundDrawablesRelative(null, null, null, null);
            String username = s.toString().trim().toLowerCase(Locale.US);
            addButton.setEnabled(false);
            if (Utils.isValidUsername(username)) {
                usernameLayout.setError(null);
                validateUsername(username);
            } else {
                if (username.length() >= 5) {
                    usernameLayout.setError(getString(R.string.invalid_username));
                } else {
                    usernameLayout.setError(null);
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {}
    };

    //endregion
}