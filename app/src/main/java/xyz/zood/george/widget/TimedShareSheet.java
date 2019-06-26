package xyz.zood.george.widget;

import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import xyz.zood.george.R;

public class TimedShareSheet {

    @NonNull private Context context;
    @NonNull private View thumb;

    public TimedShareSheet(@NonNull ConstraintLayout root) {
        context = root.getContext();

        thumb = root.findViewById(R.id.timed_share_thumb);
        if (thumb == null) {
            throw new IllegalArgumentException("'timed_share_thumb' is missing");
        }
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), context.getResources().getDimension(R.dimen.two));
            }
        });
    }

}
