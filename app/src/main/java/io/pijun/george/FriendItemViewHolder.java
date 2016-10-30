package io.pijun.george;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import im.delight.android.identicons.AsymmetricIdenticon;
import im.delight.android.identicons.SymmetricIdenticon;

public class FriendItemViewHolder extends RecyclerView.ViewHolder {

    final SymmetricIdenticon profile;
    final TextView username;
    final TextView location;
    final TextView sharePrompt;
    final Button noButton;
    final Button shareButton;

    FriendItemViewHolder(View itemView) {
        super(itemView);

        profile = (SymmetricIdenticon) itemView.findViewById(R.id.profile);
        username = (TextView) itemView.findViewById(R.id.username);
        location = (TextView) itemView.findViewById(R.id.location);
        sharePrompt = (TextView) itemView.findViewById(R.id.share_prompt);
        noButton = (Button) itemView.findViewById(R.id.dont_share);
        shareButton = (Button) itemView.findViewById(R.id.share);
    }

}
