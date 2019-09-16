package xyz.zood.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import io.pijun.george.AuthenticationManager;
import io.pijun.george.L;
import io.pijun.george.Prefs;
import io.pijun.george.WelcomeActivity;
import io.pijun.george.crypto.KeyPair;

public class MainActivity extends AppCompatActivity implements BackPressNotifier {

    @Nullable private BackPressInterceptor interceptor;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MainActivity.class);
    }

    @Override
    public void onBackPressed() {
        if (interceptor != null) {
            if (interceptor.onBackPressed()) {
                return;
            }
        }

        super.onBackPressed();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        L.i("MainActivity.onCreate");
        super.onCreate(savedInstanceState);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (!AuthenticationManager.isLoggedIn(this)) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            Prefs prefs = Prefs.get(this);
            String accessToken = prefs.getAccessToken();
            if (accessToken == null) {
                throw new RuntimeException("Access token should not be null at this point");
            }
            KeyPair keyPair = prefs.getKeyPair();
            if (keyPair == null) {
                throw new RuntimeException("Key pair should not be null at this point");
            }

            if (prefs.getShownOnboarding()) {
                MainFragment fragment = MainFragment.newInstance(accessToken, keyPair);
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(R.id.fragment_host, fragment, MainFragment.TAG);
                ft.commit();
            } else {
                OnboardingFragment fragment = OnboardingFragment.newInstance(accessToken, keyPair);
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(R.id.fragment_host, fragment, OnboardingFragment.TAG);
                ft.commit();
            }
        }
    }

    @Override
    public void setBackPressInterceptor(@Nullable BackPressInterceptor interceptor) {
        this.interceptor = interceptor;
    }
}
