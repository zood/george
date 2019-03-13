package io.pijun.george.view;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import xyz.zood.george.R;

public class ProfileListItemViewHolder extends RecyclerView.ViewHolder {

    public final AvatarView avatar;
    public final TextView username;

    public ProfileListItemViewHolder(View itemView) {
        super(itemView);

        this.avatar = itemView.findViewById(R.id.avatar);
        this.username = itemView.findViewById(R.id.username);
    }

}
