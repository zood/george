package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import xyz.zood.george.R;

public class SettingsActivity extends AppCompatActivity {

    public static final int REQUEST_EXIT = 22;

    public static Intent newIntent(@NonNull Context ctx) {
        return new Intent(ctx, SettingsActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            SettingsFragment fragment = SettingsFragment.newInstance();
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.add(R.id.root, fragment);
            ft.commit();
        }
    }
}
