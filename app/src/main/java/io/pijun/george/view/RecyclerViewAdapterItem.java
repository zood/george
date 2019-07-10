package io.pijun.george.view;

import androidx.annotation.LayoutRes;

public class RecyclerViewAdapterItem {

    @LayoutRes
    public final int viewType;
    public final long id;

    public RecyclerViewAdapterItem(@LayoutRes int viewType, long id) {
        this.viewType = viewType;
        this.id = id;
    }

}
