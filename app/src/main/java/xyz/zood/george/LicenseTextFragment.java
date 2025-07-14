package xyz.zood.george;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.io.InputStream;

import io.pijun.george.Constants;
import xyz.zood.george.databinding.FragmentLicenseTextBinding;

public class LicenseTextFragment extends Fragment {

    private static final String ARG_LICENSE_ID = "license_id";

    private @RawRes int licenseId;

    public LicenseTextFragment() {}

    public static LicenseTextFragment newInstance(@RawRes int licenseId) {
        LicenseTextFragment f = new LicenseTextFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LICENSE_ID, licenseId);

        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            throw new RuntimeException("Must create the fragment via newInstance()");
        }
        licenseId = args.getInt(ARG_LICENSE_ID);
        if (licenseId == 0) {
            throw new RuntimeException("license id was 0");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentLicenseTextBinding binding = FragmentLicenseTextBinding.inflate(inflater);
        Context ctx = inflater.getContext();
        try (InputStream is = ctx.getResources().openRawResource(licenseId)) {
            byte[] buf = new byte[1024];
            StringBuilder bldr = new StringBuilder();
            while (is.available() > 0) {
                int numRead = is.read(buf);
                bldr.append(new String(buf, 0, numRead, Constants.utf8));
            }
            binding.textView.setText(bldr.toString());
        } catch (IOException ex) {
            binding.textView.setText(ex.getLocalizedMessage());
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.textView, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            binding.textView.setPadding(0, statusBarHeight, 0, navBarHeight);

            ViewGroup.LayoutParams lp = binding.statusBarPlaceholder.getLayoutParams();
            lp.height = statusBarHeight;
            binding.statusBarPlaceholder.setLayoutParams(lp);

            return insets;
        });

        return binding.getRoot();
    }
}
