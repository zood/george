package io.pijun.george.view;

import java.lang.ref.WeakReference;

import io.pijun.george.FriendsSheetFragment;

public class FriendsSheetBehavior {

    private final WeakReference<MainLayout> mainLayoutRef;
    final FriendsSheetLayout sheetView;
    final FriendsSheetFragment fragment;

    FriendsSheetBehavior(MainLayout mainLayout, FriendsSheetLayout sheet, FriendsSheetFragment f) {
        this.mainLayoutRef = new WeakReference<>(mainLayout);
        this.sheetView = sheet;
        this.fragment = f;
    }

    public boolean isSheetExpanded() {
        MainLayout mainLayout = mainLayoutRef.get();
        if (mainLayout == null) {
            throw new RuntimeException("You shouldn't be checking this when the MainLayout isn't loaded");
        }
        return !mainLayout.sheetIsHidden;
    }

    public void setSheetState(boolean expanded) {
        MainLayout mainLayout = mainLayoutRef.get();
        if (mainLayout == null) {
            return;
        }
        mainLayout.setBottomSheetState(expanded, false);
    }

}
