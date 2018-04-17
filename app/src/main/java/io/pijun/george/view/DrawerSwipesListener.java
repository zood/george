package io.pijun.george.view;

public interface DrawerSwipesListener {
    void onOpenDrawer(float pixels);
    void onCloseDrawer(float pixels, float delta);
    void onFlingCloseDrawer();
    void onFlingOpenDrawer();
    boolean onSettleDrawer();
}
