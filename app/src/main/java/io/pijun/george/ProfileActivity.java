package io.pijun.george;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import io.pijun.george.crypto.KeyPair;
import io.pijun.george.databinding.ActivityProfileBinding;

public class ProfileActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_FILE = 2;
    private ActivityProfileBinding mBinding;

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
            Bundle extras = data.getExtras();
            if (extras == null) {
                L.i("image capture extras are null. that's wrong.");
                return;
            }
            Bitmap img = (Bitmap) extras.get("data");
            if (img == null) {
                L.i("image capture image is null. that's wrong.");
                return;
            }
            L.i("img w: " + img.getWidth() + ", h: " + img.getHeight());
            Intent i = AvatarCropperActivity.newIntent(this, img);
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
        KeyPair keyPair = prefs.getKeyPair();
        mBinding.username.setText(username);
        String hexPK = Hex.toHexString(keyPair.publicKey);
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
        mBinding.publicKey.setText(sb.toString());
    }

    public void onChangeAction(View v) {
        PopupMenu menu = new PopupMenu(this, findViewById(R.id.change_avatar));
        getMenuInflater().inflate(R.menu.profile_photo, menu.getMenu());
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.camera) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        L.i("starting activity");
                        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
                    } else {
                        L.i("onChangeAvatar has no package");
                    }
                } else if (item.getItemId() == R.id.images) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_IMAGE_FILE);
                }
                return false;
            }
        });
        menu.show();
    }
}
