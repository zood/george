package io.pijun.george;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.pijun.george.crypto.KeyPair;
import io.pijun.george.databinding.ActivityProfileBinding;

public class ProfileActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_FILE = 2;
    private ActivityProfileBinding mBinding;
    private String mImgCapturePath;

    public static Intent newIntent(@NonNull Context ctx) {
        return new Intent(ctx, ProfileActivity.class);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        L.i("onActivityResult: " + resultCode);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            Uri uri = Uri.fromFile(new File(mImgCapturePath));
            Intent i = AvatarCropperActivity.newIntent(this, uri);
            startActivity(i);
        } else if (requestCode == REQUEST_IMAGE_FILE) {
            Uri uri = data.getData();
            if (uri == null) {
                L.i("image file data is null");
                return;
            }
            L.i("img uri: " + uri.toString());
            Intent i = AvatarCropperActivity.newIntent(this, uri);
            startActivity(i);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_profile);

        Prefs prefs = Prefs.get(this);
        String username = prefs.getUsername();
        mBinding.username.setText(username);
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair != null) {
            String hexPK = Hex.toHexString(keyPair.publicKey);
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < hexPK.length()) {
                for (int j = 0; j < 4; j++) {
                    sb.append(hexPK.charAt(i++));
                    sb.append(hexPK.charAt(i++));
                    sb.append(hexPK.charAt(i++));
                    sb.append(hexPK.charAt(i++));
                    sb.append(" ");
                }
                sb.append("\n");
            }
            mBinding.publicKey.setText(sb.toString());
        } else {
            // This should never happen
            mBinding.publicKey.setText(R.string.error_loading_key);
        }
    }

    public void onChangeAction(View v) {
        PopupMenu menu = new PopupMenu(this, findViewById(R.id.change_avatar));
        getMenuInflater().inflate(R.menu.profile_photo, menu.getMenu());
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
    protected void onStart() {
        super.onStart();

        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                mBinding.avatar.setImage(bitmap);
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                Prefs prefs = Prefs.get(ProfileActivity.this);
                String username = prefs.getUsername();
                int size = mBinding.avatar.getWidth();
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Identicon.draw(bmp, username);
                mBinding.avatar.setImage(bmp);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
        // store the target in the view tag because of
        // https://stackoverflow.com/a/26918731/211180
        mBinding.avatar.setTag(target);
        Picasso.with(this).
                load(AvatarManager.getMyAvatar(this)).
                into(target);
    }

    private void startCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File imgFile = File.createTempFile(timeStamp, ".jpg", storageDir);
                mImgCapturePath = imgFile.getAbsolutePath();
                Uri photoUri  = FileProvider.getUriForFile(this, "io.pijun.george.fileprovider", imgFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            } else {
                L.i("no package available to handle REQUEST_IMAGE_CAPTURE");
            }
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
}
