package io.pijun.george.view;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import io.pijun.george.R;

public class SimpleListItemViewHolder extends RecyclerView.ViewHolder {

    public final TextView title;
    public final TextView detail;

    public SimpleListItemViewHolder(View itemView) {
        super(itemView);

        title = itemView.findViewById(R.id.title);
        detail = itemView.findViewById(R.id.detail);
    }

}
