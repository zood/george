package xyz.zood.george;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;

import io.pijun.george.App;
import io.pijun.george.Prefs;

// ThemeDialog lets the user pick the system, light, or dark theme. Picking one applies it and
// closes the dialog.
public class ThemeDialog extends DialogFragment {

    // The choices in fragment_theme_dialog.xml, in order
    private static final int[] MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
    };

    public static ThemeDialog newInstance() {
        return new ThemeDialog();
    }

    //region lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_theme_dialog, container, false);

        Context ctx = requireContext().getApplicationContext();
        Prefs prefs = Prefs.get(ctx);
        int current = prefs.getNightMode();
        RadioGroup choices = root.findViewById(R.id.choices);
        for (int i = 0; i < MODES.length; i++) {
            int mode = MODES[i];
            View choice = choices.getChildAt(i);
            if (mode == current) {
                choices.check(choice.getId());
            }
            // a click listener, rather than a checked change listener, so picking the current
            // theme closes the dialog too
            choice.setOnClickListener(v -> {
                // dismiss before applying, so the dialog isn't restored when the activity is
                // recreated in the new theme
                dismissNow();
                if (mode != current) {
                    prefs.setNightMode(mode);
                    App.applyNightMode(mode);
                }
            });
        }
        root.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());

        // give the window rounded corners
        Window win = requireDialog().getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(R.drawable.rounded_background);
        }

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Because we used a custom background on onCreateView, we need to re-apply the layout size
        // This doesn't work if we do it in onCreateView
        Window win = requireDialog().getWindow();
        if (win != null) {
            win.setLayout((int)getResources().getDimension(R.dimen.dialog_width), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    //endregion
}
