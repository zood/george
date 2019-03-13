package io.pijun.george.view;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class SimpleListItemViewHolder extends RecyclerView.ViewHolder {

    public final TextView title;

    public SimpleListItemViewHolder(View itemView) {
        super(itemView);

        title = (TextView) itemView;
    }

}
