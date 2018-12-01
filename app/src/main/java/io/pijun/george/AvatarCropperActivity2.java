package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.IOException;

import io.pijun.george.databinding.ActivityAvatarCropper2Binding;

public class AvatarCropperActivity2 extends AppCompatActivity implements CropImageView.OnCropImageCompleteListener {

    private static String ARG_URI = "uri";

    private ActivityAvatarCropper2Binding binding;

    public static Intent newIntent(@NonNull Context ctx, @NonNull Uri uri) {
        Intent i = new Intent(ctx, AvatarCropperActivity2.class);
        i.putExtra(ARG_URI, uri);
        return i;
    }

    @UiThread
    public void onCancel(View v) {
        finish();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_avatar_cropper2);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            throw new IllegalArgumentException("You need to use newIntent to start the activity");
        }
        Uri uri = extras.getParcelable(ARG_URI);
        if (uri == null) {
            throw new IllegalArgumentException("You promised me a uri");
        }
        binding.cropImageView.setImageUriAsync(uri);
    }

    public void onDone(View v) {
        binding.cancel.setEnabled(false);
        binding.done.setEnabled(false);
        binding.cropImageView.setEnabled(false);

        binding.cropImageView.setOnCropImageCompleteListener(this);
        binding.cropImageView.getCroppedImageAsync();
    }

    //region OnCropImageCompleteListener

    @Override
    @UiThread
    public void onCropImageComplete(CropImageView view, CropImageView.CropResult result) {
        L.i("onCropImageComplete");
        Bitmap bmp = result.getBitmap();
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    boolean success = AvatarManager.setMyAvatar(AvatarCropperActivity2.this, bmp);
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            if (success) {
                                finish();
                            } else {
                                Utils.showAlert(AvatarCropperActivity2.this, R.string.save_error,  R.string.unknown_avatar_save_error_msg, getSupportFragmentManager());
                                binding.done.setEnabled(true);
                                binding.cancel.setEnabled(true);
                                binding.cropImageView.setEnabled(true);
                            }
                        }
                    });
                } catch (IOException ex) {
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            Utils.showAlert(AvatarCropperActivity2.this, R.string.save_error, R.string.avatar_save_io_error_msg, getSupportFragmentManager());
                            binding.done.setEnabled(true);
                            binding.cancel.setEnabled(true);
                            binding.cropImageView.setEnabled(true);
                        }
                    });
                }
            }
        });
    }


    //endregion
}
