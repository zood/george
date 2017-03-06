package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class TestActivity extends AppCompatActivity {

    public static Intent newIntent(Context context) {
        return new Intent(context, TestActivity.class);
    }

    private int mCurrentTask = WelcomeLayout.TASK_SPLASH;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_welcome);
    }

    @Override
    public void onBackPressed() {
        if (mCurrentTask == WelcomeLayout.TASK_SIGN_IN || mCurrentTask == WelcomeLayout.TASK_REGISTER) {
            mCurrentTask = WelcomeLayout.TASK_SPLASH;
            WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
            root.setTask(mCurrentTask, true);
            return;
        }

        super.onBackPressed();
    }

    public void onSignInAction(View v) {
        mCurrentTask = WelcomeLayout.TASK_SIGN_IN;
        WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        root.setTask(WelcomeLayout.TASK_SIGN_IN, true);

        root.setCloudMovementEnabled(true);
    }

    public void onRegisterAction(View v) {
        mCurrentTask = WelcomeLayout.TASK_REGISTER;
        WelcomeLayout root = (WelcomeLayout) findViewById(R.id.root);
        root.setTask(WelcomeLayout.TASK_REGISTER, true);
    }
}
