package xyz.zood.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.R;
import io.pijun.george.UiRunnable;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.UserRecord;
import io.pijun.george.databinding.ActivitySafetyNumberBinding;

public class SafetyNumberActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {

    private static final String ARG_USERNAME = "username";

    private ActivitySafetyNumberBinding binding;
    private String safetyNumber;

    public static Intent newIntent(@NonNull Context ctx, UserRecord otherUser) {
        Intent i = new Intent(ctx, SafetyNumberActivity.class);
        i.putExtra(ARG_USERNAME, otherUser.username);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_safety_number);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("You need to use newIntent to start the activity");
        }
        String otherUsername = extras.getString(ARG_USERNAME, null);
        if (TextUtils.isEmpty(otherUsername)) {
            throw new IllegalArgumentException("You need to use newIntent to start the activity");
        }

        binding.instructions.setText(getString(R.string.safety_number_instructions_msg, otherUsername));

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                UserRecord otherUser = DB.get().getUser(otherUsername);
                if (otherUser == null) {
                    return;
                }
                Prefs prefs = Prefs.get(SafetyNumberActivity.this);
                String myUsername = prefs.getUsername();
                if (TextUtils.isEmpty(myUsername)) {
                    return;
                }
                KeyPair keyPair = prefs.getKeyPair();
                if (keyPair == null) {
                    return;
                }

                byte[] firstKey;
                byte[] secondKey;
                if (otherUsername.compareToIgnoreCase(myUsername) < 0) {
                    firstKey = otherUser.publicKey;
                    secondKey = keyPair.publicKey;
                } else {
                    firstKey = keyPair.publicKey;
                    secondKey = otherUser.publicKey;
                }

                byte[] bytes = new byte[firstKey.length + secondKey.length];
                System.arraycopy(firstKey, 0, bytes, 0, firstKey.length);
                System.arraycopy(secondKey, 0, bytes, firstKey.length, secondKey.length);

                safetyNumber = SafetyNumber.toSafetyNumber(bytes, 4, "  ");
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        binding.safetyNumber.setText(safetyNumber);
                    }
                });
            }
        });

        binding.toolbar.inflateMenu(R.menu.activity_safety_number);
        binding.toolbar.setOnMenuItemClickListener(this);
        binding.toolbar.setNavigationContentDescription(R.string.back);
        binding.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.share_safety_number) {
            L.i("Time to share the safety number");
            String msg = getString(R.string.share_safety_number_msg, safetyNumber);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_safety_number_via_ellipsis)));
            return true;
        }

        return false;
    }
}
