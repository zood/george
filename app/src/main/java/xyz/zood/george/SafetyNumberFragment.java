package xyz.zood.george;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import io.pijun.george.App;
import io.pijun.george.Constants;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;
import xyz.zood.george.databinding.FragmentSafetyNumberBinding;

public class SafetyNumberFragment extends Fragment {

    private static final String ARG_MY_USERNAME = "my_username";
    private static final String ARG_MY_PUBLIC_KEY = "my_public_key";
    private static final String ARG_OTHER_USERNAME = "other_username";
    private static final String ARG_OTHER_PUBLIC_KEY = "other_public_key";

    private FragmentSafetyNumberBinding binding;
    private String safetyNumber;
    private String otherUsername;

    static SafetyNumberFragment newInstance(@NonNull String myUsername, @NonNull byte[] myPublicKey, @NonNull String otherUsername, @NonNull byte[] otherPublicKey) {
        SafetyNumberFragment fragment = new SafetyNumberFragment();

        Bundle args = new Bundle();
        args.putString(ARG_MY_USERNAME, myUsername);
        args.putByteArray(ARG_MY_PUBLIC_KEY, myPublicKey);
        args.putString(ARG_OTHER_USERNAME, otherUsername);
        args.putByteArray(ARG_OTHER_PUBLIC_KEY, otherPublicKey);

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
        final String myUsername = args.getString(ARG_MY_USERNAME);
        if (myUsername == null || myUsername.isEmpty()) {
            throw new RuntimeException("missing my username");
        }
        otherUsername = args.getString(ARG_OTHER_USERNAME);
        if (otherUsername == null || otherUsername.isEmpty()) {
            throw new RuntimeException("missing other username");
        }
        final byte[] myPublicKey = args.getByteArray(ARG_MY_PUBLIC_KEY);
        if (myPublicKey == null || myPublicKey.length != Constants.PUBLIC_KEY_LENGTH) {
            throw new RuntimeException("missing my public key");
        }
        final byte[] otherPublicKey = args.getByteArray(ARG_OTHER_PUBLIC_KEY);
        if (otherPublicKey == null || otherPublicKey.length != Constants.PUBLIC_KEY_LENGTH) {
            throw new RuntimeException("missing other public key");
        }

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                byte[] firstKey;
                byte[] secondKey;
                if (otherUsername.compareToIgnoreCase(myUsername) < 0) {
                    firstKey = otherPublicKey;
                    secondKey = myPublicKey;
                } else {
                    firstKey = myPublicKey;
                    secondKey = otherPublicKey;
                }

                byte[] bytes = new byte[firstKey.length + secondKey.length];
                System.arraycopy(firstKey, 0, bytes, 0, firstKey.length);
                System.arraycopy(secondKey, 0, bytes, firstKey.length, secondKey.length);

                safetyNumber = SafetyNumber.toSafetyNumber(bytes, 4, "  ");
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        if (binding == null) {
                            return;
                        }
                        binding.safetyNumber.setText(safetyNumber);
                    }
                });
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_safety_number, container, false);
        binding.instructions.setText(getString(R.string.safety_number_instructions_msg, otherUsername));
        binding.safetyNumber.setText(safetyNumber);
        binding.back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireFragmentManager().popBackStack();
            }
        });
        binding.share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShare();
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        binding = null;

        super.onDestroy();
    }

    private void onShare() {
        String msg = getString(R.string.share_safety_number_msg, safetyNumber);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, msg);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_safety_number_via_ellipsis)));
    }
}
