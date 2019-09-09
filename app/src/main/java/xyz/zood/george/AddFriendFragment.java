package xyz.zood.george;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.Locale;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.UiRunnable;
import io.pijun.george.Utils;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.SearchUserResult;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.UserRecord;
import retrofit2.Response;
import xyz.zood.george.databinding.FragmentAddFriendBinding;

public class AddFriendFragment extends Fragment {

    private static final String ARG_ACCESS_TOKEN = "access_token";
    private static final String ARG_KEY_PAIR = "key_pair";

    private String accessToken;
    private FragmentAddFriendBinding binding;
    private Drawable invalidUserIcon;
    private Drawable validUserIcon;
    private FriendshipManager friendshipManager;

    static AddFriendFragment newInstance(@NonNull String accessToken, @NonNull KeyPair keyPair) {
        AddFriendFragment fragment = new AddFriendFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCESS_TOKEN, accessToken);
        args.putParcelable(ARG_KEY_PAIR, keyPair);

        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            throw new RuntimeException("You must create the fragment via newInstance");
        }
        accessToken = args.getString(ARG_ACCESS_TOKEN);
        if (accessToken == null) {
            throw new RuntimeException("missing access token");
        }
        KeyPair keyPair = args.getParcelable(ARG_KEY_PAIR);
        if (keyPair == null) {
            throw new RuntimeException("missing key pair");
        }

        Context ctx = requireContext();
        friendshipManager = new FriendshipManager(ctx, DB.get(), accessToken, keyPair);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_add_friend, container, false);
        binding.username.addTextChangedListener(
                new UsernameWatcher(
                        binding.usernameContainer, binding.username));
        binding.usernameContainer.setEndIconVisible(true);
        binding.back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireFragmentManager().popBackStack();
            }
        });
        binding.inviteFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FriendshipManager.inviteFriend(requireContext());
            }
        });
        binding.addFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddFriendAction();
            }
        });

        int lightRed = getResources().getColor(R.color.zood_red_light);
        invalidUserIcon = getResources().getDrawable(R.drawable.ic_close_black_24dp).mutate();
        invalidUserIcon.setTint(lightRed);

        int green = getResources().getColor(R.color.zood_green);
        validUserIcon = getResources().getDrawable(R.drawable.ic_check_black_24dp).mutate();
        validUserIcon.setTint(green);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        invalidUserIcon = null;
        validUserIcon = null;

        super.onDestroyView();
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

    @UiThread
    private void onAddFriendAction() {
        Editable text = binding.username.getText();
        if (text == null) {
            throw new NullPointerException("How did we get into this state?");
        }
        String username = text.toString().trim().toLowerCase(Locale.US);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                friendshipManager.addFriend(username, AddFriendFragment.this::onAddFriendFinished);
            }
        });
    }

    @WorkerThread
    private void onAddFriendFinished(@NonNull FriendshipManager.AddFriendResult result, @Nullable String extraInfo) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                String errMsg;
                switch (result) {
                    case Success:
                        requireFragmentManager().popBackStack();
                        return;
                    case UserNotFound:
                        errMsg = getString(R.string.unknown_error_getting_user_info);
                        break;
                    case DatabaseError:
                        errMsg = getString(R.string.database_error_msg, extraInfo);
                        break;
                    case SharingGrantFailed:
                        errMsg = getString(R.string.sharing_grant_failed_msg, extraInfo);
                        break;
                    default:
                        throw new RuntimeException("unaccounted for result");
                }

                AlertDialog.Builder bldr = new AlertDialog.Builder(requireContext());
                bldr.setTitle(R.string.error);
                bldr.setMessage(errMsg);
                bldr.setPositiveButton(R.string.ok, null);
                bldr.setCancelable(true);
                bldr.show();
            }
        });
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
