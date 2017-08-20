package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    public static Intent newIntent(@NonNull Context ctx) {
        return new Intent(ctx, ProfileActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_profile);
    }
}
