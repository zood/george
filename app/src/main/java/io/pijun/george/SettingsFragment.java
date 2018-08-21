package io.pijun.george;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.pijun.george.crypto.KeyPair;
import io.pijun.george.databinding.FragmentSettingsBinding;

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
        Prefs prefs = Prefs.get(requireContext());
        adapter.username = prefs.getUsername();
        KeyPair kp = prefs.getKeyPair();
        if (kp != null) {
            adapter.publicKey = kp.publicKey;
        }

        AvatarManager.addListener(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        binding.list.setAdapter(adapter);
        binding.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireActivity().finish();
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

    //region SettingsAdapter.Listener methods

    @Override
    public void onAboutAction() {

    }

    @Override
    public void onChangeProfilePhoto(View anchor) {
        // Check if there is a camera on this device.
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Context ctx = requireContext();
        if (intent.resolveActivity(ctx.getPackageManager()) == null) {
            // no camera, so just show the image picke
            startImagePicker();
            return;
        }

        // Show a popup menu to let the user pick
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        requireActivity().getMenuInflater().inflate(R.menu.profile_photo, menu.getMenu());
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.camera) {
                    startCamera();
                } else if (item.getItemId() == R.id.images) {
                    startImagePicker();
                }
                return false;
            }
        });
        menu.show();
    }

    @Override
    public void onLogOutAction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        builder.setMessage(R.string.confirm_log_out_msg);
        builder.setCancelable(true);
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.log_out, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AuthenticationManager.get().logOut(requireContext(), new AuthenticationManager.LogoutWatcher() {
                    @Override
                    public void onUserLoggedOut() {
                        requireActivity().finish();
                    }
                });
            }
        });
        builder.show();
    }

    @Override
    public void onShowPublicKey() {
        KeyPair kp = Prefs.get(requireContext()).getKeyPair();
        if (kp == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        builder.setTitle(R.string.your_public_key);
        String hexPK = Hex.toHexString(kp.publicKey);
        StringBuilder sb = new StringBuilder();
        int i=0;
        while (i < hexPK.length()) {
            for (int j=0; j<4; j++) {
                sb.append(hexPK.charAt(i++));
                sb.append(hexPK.charAt(i++));
                sb.append(hexPK.charAt(i++));
                sb.append(hexPK.charAt(i++));
                sb.append(" ");
            }
            sb.append("\n");
        }
        builder.setMessage(sb.toString());
        builder.setPositiveButton(R.string.ok, null);
        AlertDialog dialog = builder.show();
        TextView msgView = dialog.findViewById(android.R.id.message);
        if (msgView != null) {
            msgView.setTypeface(Typeface.MONOSPACE);
        }
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
                L.i("image file data is null");
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
            Uri photoUri  = FileProvider.getUriForFile(ctx, "io.pijun.george.fileprovider", imgFile);
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
        L.i("SettingsFragment.onAvatarUpdated: " + username);
        if (username != null) {
            // we're only interested in updates to our personal avatar
            return;
        }

        L.i("notifyDataSetChanged");
        adapter.notifyDataSetChanged();
    }

    //endregion
}
