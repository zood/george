package xyz.zood.george.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.pijun.george.R;

public class AuthenticationProgressDialog extends DialogFragment {

    public AuthenticationProgressDialog() {
        setCancelable(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_authentication_progress_dialog, container, false);

        // give the window rounded corners
        Window win = getDialog().getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(R.drawable.rounded_background);
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Because we used a custom background on onCreateView, we need to re-apply the layout size
        // This doesn't work if we do it in onCreateView
        Window win = getDialog().getWindow();
        if (win != null) {
            win.setLayout((int)getResources().getDimension(R.dimen.dialog_width), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
