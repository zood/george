package io.pijun.george;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

public class FriendItemViewHolder extends RecyclerView.ViewHolder implements AddressBinder.AddressReceiver {

    final Identicon profile;
    final TextView username;
    final TextView location;
    private AddressBinder mBinder;

    FriendItemViewHolder(View itemView) {
        super(itemView);

        profile = (Identicon) itemView.findViewById(R.id.profile);
        username = (TextView) itemView.findViewById(R.id.username);
        location = (TextView) itemView.findViewById(R.id.location);
    }

    @Override
    public AddressBinder getAddressBinder() {
        return mBinder;
    }

    @Override
    public void setAddressBinder(AddressBinder binder) {
        this.mBinder = binder;
    }

    @Override
    public void onAddressResolved(@Nullable String address) {
        location.setText(address);
    }
}
