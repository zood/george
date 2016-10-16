package io.pijun.george;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class FriendItemViewHolder extends RecyclerView.ViewHolder {

    final ImageView profile;
    final TextView username;
    final TextView location;

    public FriendItemViewHolder(View itemView) {
        super(itemView);

        profile = (ImageView) itemView.findViewById(R.id.profile);
        username = (TextView) itemView.findViewById(R.id.username);
        location = (TextView) itemView.findViewById(R.id.location);
    }

}
