package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import xyz.zood.george.AvatarCropperActivity;
import xyz.zood.george.AvatarManager;
import xyz.zood.george.FriendshipManager;
import xyz.zood.george.LicensesFragment;
import xyz.zood.george.R;
import xyz.zood.george.databinding.FragmentSettingsBinding;
import xyz.zood.george.widget.ZoodDialog;

public class SettingsFragment extends Fragment implements SettingsAdapter.Listener, AvatarManager.Listener {

    private FragmentSettingsBinding binding;
    private SettingsAdapter adapter;
    private String imgCapturePath;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private ActivityResultLauncher<Uri> takePicture;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    // applySystemUIInsets applies the system view insets. It should only be called once after the fragment's view is created.
    private void applySystemUIInsets(FragmentSettingsBinding binding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsView, (v, insets) -> {
            int statusBarHeight;
            statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            ConstraintLayout.LayoutParams sbLP = (ConstraintLayout.LayoutParams) binding.statusBarPlaceholder.getLayoutParams();
            sbLP.height = statusBarHeight;
            binding.statusBarPlaceholder.setLayoutParams(sbLP);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    //region Lifecycle
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new SettingsAdapter(this);
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) {
                return;
            }

            Intent i = AvatarCropperActivity.newIntent(requireContext(), uri);
            startActivity(i);
        });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (!success) {
                return;
            }

            Uri uri = Uri.fromFile(new File(imgCapturePath));
            Intent i = AvatarCropperActivity.newIntent(requireContext(), uri);
            startActivity(i);
        });

        AvatarManager.addListener(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        binding.list.setAdapter(adapter);
        binding.back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getParentFragmentManager().popBackStack();
            }
        });

        Prefs prefs = Prefs.get(requireContext());
        String username = prefs.getUsername();
        binding.avatar.setUsername(username);
        Context ctx = requireContext();
        File myImg = AvatarManager.getMyAvatar(ctx);
        Picasso.with(ctx).load(myImg).into(binding.avatar);
        binding.username.setText(username);
        binding.profileItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChangeProfilePhoto(binding.avatar);
            }
        });

        applySystemUIInsets(binding);

        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        AvatarManager.removeListener(this);

        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }

    //endregion

    private void onChangeProfilePhoto(View anchor) {
        // Check if there is a camera on this device.
        Context ctx = requireContext();
        PackageManager pm = ctx.getPackageManager();
        boolean hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        if (!hasCamera) {
            // no camera, so just show the avatar picker
            startImagePicker();
            return;
        }

        // Show a popup menu to let the user pick
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        MenuInflater menuInflater = new MenuInflater(requireContext());
        menuInflater.inflate(R.menu.profile_photo, menu.getMenu());
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.camera) {
                    startCamera();
                } else if (item.getItemId() == R.id.images) {
                    startImagePicker();
                }
                return true;
            }
        });
        menu.show();
    }

    //region SettingsAdapter.Listener methods

    @Override
    public void onAboutAction() {
        Context ctx = requireContext();
        PackageManager pkgMgr = ctx.getPackageManager();
        try {
            PackageInfo pi = pkgMgr.getPackageInfo("xyz.zood.george", 0);
            long versionCode = pi.getLongVersionCode();
            String msg = getString(R.string.app_version_msg, pi.versionName, versionCode);
            ZoodDialog dialog = ZoodDialog.newInstance(msg);
            dialog.setTitle(ctx.getString(R.string.app_name));
            dialog.setButton1(ctx.getString(R.string.ok), null);
            dialog.setButton2(ctx.getString(R.string.licenses), v -> onShowLicensesClicked());
            dialog.show(getParentFragmentManager(), null);
        } catch (PackageManager.NameNotFoundException ignore) {
            throw new RuntimeException("You need to specify the correct package name");
        }
    }

    public void onInviteFriendAction() {
        FriendshipManager.inviteFriend(requireContext());
    }

    @Override
    public void onLogOutAction() {
        ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.confirm_log_out_msg));
        dialog.setButton1(getString(R.string.log_out), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AuthenticationManager.get().logOut(requireContext(), new AuthenticationManager.LogoutWatcher() {
                    @Override
                    public void onUserLoggedOut() {
                        requireActivity().finish();
                    }
                });
            }
        });
        dialog.setButton2(getString(R.string.no), null);
        FragmentManager fm = getParentFragmentManager();
        dialog.show(fm, null);
    }

    public void onNotificationsClicked() {

    }

    public void onPrivacyPolicyClicked() {
        String url = "https://www.zood.xyz/privacy/mobile-apps";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    private void onShowLicensesClicked() {
        LicensesFragment fragment = LicensesFragment.newInstance();
        FragmentManager mgr = getParentFragmentManager();
        mgr.beginTransaction()
                .setCustomAnimations(R.animator.new_enter_from_right,
                        R.animator.new_exit_to_left,
                        R.animator.new_enter_from_left,
                        R.animator.new_exit_to_right)
                .replace(R.id.fragment_host, fragment)
                .addToBackStack(null)
                .commit();
    }

    //endregion

    //region Profile photo taking/selection

    private void startCamera() {
        try {
            Context ctx = requireContext();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File storageDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File imgFile = File.createTempFile(timeStamp, ".jpg", storageDir);
            imgCapturePath = imgFile.getAbsolutePath();
            Uri photoUri  = FileProvider.getUriForFile(ctx, "xyz.zood.george.fileprovider", imgFile);
            takePicture.launch(photoUri);
        } catch (Throwable t) {
            L.w("startCamera", t);
        }
    }

    private void startImagePicker() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    //endregion

    //region AvatarManager.Listener

    @Override
    public void onAvatarUpdated(@Nullable String username) {
        if (username != null) {
            // we're only interested in updates to our personal avatar
            return;
        }

        Context ctx = getContext();
        if (ctx == null || binding == null) {
            return;
        }

        File myImg = AvatarManager.getMyAvatar(ctx);
        Picasso.with(ctx).load(myImg).into(binding.avatar);
    }

    //endregion
}
