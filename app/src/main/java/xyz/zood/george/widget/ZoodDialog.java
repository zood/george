package xyz.zood.george.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;

import xyz.zood.george.R;

public class ZoodDialog extends DialogFragment {

    @Nullable private String title;
    @Nullable private String message;
    @Nullable private String button1Text;
    @Nullable private View.OnClickListener button1Listener;
    @Nullable private String button2Text;
    @Nullable private View.OnClickListener button2Listener;

    public static ZoodDialog newInstance(@NonNull String message) {
        ZoodDialog zad = new ZoodDialog();
        zad.message = message;
        return zad;
    }

    //region lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root;
        if (title != null) {
            root = inflater.inflate(R.layout.fragment_confirmation_dialog, container, false);
            TextView titleView = root.findViewById(R.id.title);
            titleView.setText(title);
        } else {
            root = inflater.inflate(R.layout.fragment_alert_dialog, container, false);
        }

        TextView messageView = root.findViewById(R.id.message);
        Button button1 = root.findViewById(R.id.button1);
        Button button2 = root.findViewById(R.id.button2);

        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);

        if (button1Text != null) {
            button1.setText(button1Text);
            button1.setVisibility(View.VISIBLE);
            button1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    requireDialog().dismiss();
                    if (button1Listener != null) {
                        button1Listener.onClick(v);
                    }
                }
            });
        }
        if (button2Text != null) {
            button2.setText(button2Text);
            button2.setVisibility(View.VISIBLE);
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requireDialog().dismiss();
                    if (button2Listener != null) {
                        button2Listener.onClick(v);
                    }
                }
            });
        }

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

    //region setters

    @UiThread
    public void setButton1(@NonNull String text, @Nullable View.OnClickListener listener) {
        button1Text = text;
        button1Listener = listener;
    }

    @UiThread
    public void setButton2(@NonNull String text, @Nullable View.OnClickListener listener) {
        button2Text = text;
        button2Listener = listener;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    //endregion
}
