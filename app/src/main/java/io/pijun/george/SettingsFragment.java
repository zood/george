package io.pijun.george;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
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

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_FILE = 2;

    private FragmentSettingsBinding binding;
    private SettingsAdapter adapter;
    private String imgCapturePath;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    //region Lifecycle
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new SettingsAdapter(this);

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
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Context ctx = requireContext();
        if (intent.resolveActivity(ctx.getPackageManager()) == null) {
            // no camera, so just show the avatar picke
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        L.i("onActivityResult: " + resultCode);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        Context ctx = requireContext();
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            Uri uri = Uri.fromFile(new File(imgCapturePath));

            Intent i = AvatarCropperActivity.newIntent(ctx, uri);
            startActivity(i);
        } else if (requestCode == REQUEST_IMAGE_FILE) {
            Uri uri = data.getData();
            if (uri == null) {
                L.i("avatar file data is null");
                return;
            }
            L.i("img uri: " + uri.toString());

            Intent i = AvatarCropperActivity.newIntent(ctx, uri);
            startActivity(i);
        }
    }

    private void startCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Context ctx = requireContext();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File storageDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File imgFile = File.createTempFile(timeStamp, ".jpg", storageDir);
            imgCapturePath = imgFile.getAbsolutePath();
            Uri photoUri  = FileProvider.getUriForFile(ctx, "xyz.zood.george.fileprovider", imgFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } catch (Throwable t) {
            L.w("unable to start camera", t);
            CloudLogger.log(t);
        }
    }

    private void startImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_FILE);
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
