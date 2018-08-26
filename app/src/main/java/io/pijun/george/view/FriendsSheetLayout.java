package io.pijun.george.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.constraintlayout.widget.ConstraintLayout;

public class FriendsSheetLayout extends ConstraintLayout {
    public float hiddenStateTranslationY;

    public FriendsSheetLayout(Context context) {
        super(context);
    }

    public FriendsSheetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FriendsSheetLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
