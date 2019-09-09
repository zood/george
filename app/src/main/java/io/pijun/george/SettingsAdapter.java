package io.pijun.george;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;
import io.pijun.george.view.ProfileListItemViewHolder;
import io.pijun.george.view.RecyclerViewAdapterItem;
import xyz.zood.george.AvatarManager;
import xyz.zood.george.R;
import xyz.zood.george.widget.SettingsListItemViewHolder;

public class SettingsAdapter extends RecyclerView.Adapter {

    private static final int PROFILE_ID = 1;
    private static final int LOG_OUT_ID = 2;
    private static final int BILLING_PLAN_ID = 3;
    private static final int MANAGE_FAMILY_MEMBERS = 4;
    private static final int ABOUT_ID = 5;
    private static final int HELP_AND_FEEDBACK_ID = 6;
    private static final int INVITE_A_FRIEND = 7;
    private static final int NOTIFICATIONS = 8;

    private final ArrayList<RecyclerViewAdapterItem> adapterItems = new ArrayList<>();
    String username;
    byte[] publicKey;
    @NonNull
    private final SettingsAdapter.Listener listener;

    SettingsAdapter(@NonNull Listener listener) {
        this.listener = listener;
        rebuildRecycler();
        setHasStableIds(true);
    }

    //region RecyclerView overrides
    @Override
    public int getItemCount() {
        return adapterItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return adapterItems.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(viewType, parent, false);
        switch (viewType) {
            case R.layout.settings_list_item:
                return new SettingsListItemViewHolder(v);
            case R.layout.profile_list_item:
                return new ProfileListItemViewHolder(v);
            default:
                throw new RuntimeException("Unknown view type");
        }
    }

    @Override @UiThread
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RecyclerViewAdapterItem item = adapterItems.get(position);
        switch ((int) item.id) {
            case PROFILE_ID: {
                ProfileListItemViewHolder h = (ProfileListItemViewHolder) holder;
                h.avatar.setUsername(username);
                File myImg = AvatarManager.getMyAvatar(h.avatar.getContext());
                Picasso.with(h.avatar.getContext()).load(myImg).into(h.avatar);

                h.username.setText(username);
                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onChangeProfilePhoto(h.avatar);
                    }
                });
                break;
            }
            case LOG_OUT_ID: {
                SettingsListItemViewHolder h = (SettingsListItemViewHolder) holder;
                h.textView.setText(R.string.log_out);
                h.textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onLogOutAction();
                    }
                });
                h.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_outlined_exit_to_app_24dp,
                        0,
                        0,
                        0);
                break;
            }
            case ABOUT_ID: {
                SettingsListItemViewHolder h = (SettingsListItemViewHolder) holder;
                h.textView.setText(R.string.about);
                h.textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onAboutAction();
                    }
                });
                h.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_info_24dp,
                        0,
                        0,
                        0);
                break;
            }
            case BILLING_PLAN_ID: {
                SettingsListItemViewHolder h = (SettingsListItemViewHolder) holder;
                h.textView.setText("Individual plan");
                break;
            }
            case INVITE_A_FRIEND: {
                SettingsListItemViewHolder h = (SettingsListItemViewHolder) holder;
                h.textView.setText(R.string.invite_a_friend);
                h.textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onInviteFriendAction();
                    }
                });
                h.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_outlined_person_add_24dp,
                        0,
                        0,
                        0);
                break;
            }
            case NOTIFICATIONS: {
                SettingsListItemViewHolder h = (SettingsListItemViewHolder) holder;
                h.textView.setText(R.string.notifications);
                h.textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onNotificationsClicked();
                    }
                });
                h.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_outlined_notifications_24dp,
                        0,
                        0,
                        0);
            }
        }
    }
    //endregion

    private void rebuildRecycler() {
        adapterItems.add(new RecyclerViewAdapterItem(R.layout.profile_list_item, PROFILE_ID));
        adapterItems.add(new RecyclerViewAdapterItem(R.layout.settings_list_item, NOTIFICATIONS));
        adapterItems.add(new RecyclerViewAdapterItem(R.layout.settings_list_item, ABOUT_ID));
        adapterItems.add(new RecyclerViewAdapterItem(R.layout.settings_list_item, INVITE_A_FRIEND));
        adapterItems.add(new RecyclerViewAdapterItem(R.layout.settings_list_item, LOG_OUT_ID));
    }

    interface Listener {
        @UiThread void onAboutAction();
        @UiThread void onChangeProfilePhoto(View anchor);
        @UiThread void onInviteFriendAction();
        @UiThread void onLogOutAction();
        @UiThread void onNotificationsClicked();
    }

}
