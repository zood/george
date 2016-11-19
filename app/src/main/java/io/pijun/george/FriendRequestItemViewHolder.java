package io.pijun.george;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import im.delight.android.identicons.SymmetricIdenticon;

public class FriendRequestItemViewHolder extends RecyclerView.ViewHolder {

    final SymmetricIdenticon profile;
    final TextView username;
    final TextView sharePrompt;
    final Button noButton;
    final Button shareButton;

    FriendRequestItemViewHolder(View itemView) {
        super(itemView);

        profile = (SymmetricIdenticon) itemView.findViewById(R.id.profile);
        username = (TextView) itemView.findViewById(R.id.username);
        sharePrompt = (TextView) itemView.findViewById(R.id.share_prompt);
        noButton = (Button) itemView.findViewById(R.id.dont_share);
        shareButton = (Button) itemView.findViewById(R.id.share);
    }

}