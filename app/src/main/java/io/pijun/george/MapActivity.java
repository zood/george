package io.pijun.george;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

public class MapActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (Prefs.get(this).getAccessToken() == null) {
            Intent loginIntent = LoginActivity.newIntent(this);
            startActivity(loginIntent);
            finish();
        }
    }
}
