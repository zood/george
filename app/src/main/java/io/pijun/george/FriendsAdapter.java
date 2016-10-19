package io.pijun.george;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import io.pijun.george.models.FriendRecord;

class FriendsAdapter extends RecyclerView.Adapter {

    private ArrayList<FriendRecord> mRecords = new ArrayList<>();

    FriendsAdapter(Context context) {
        reloadFriends(context);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.friend_item, parent, false);
        return new FriendItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        FriendItemViewHolder h = (FriendItemViewHolder) holder;
        FriendRecord record = mRecords.get(position);
        h.username.setText(record.username);
    }

    @Override
    public int getItemCount() {
        return mRecords.size();
    }

    @AnyThread
    void reloadFriends(final Context context) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                DB db = DB.get(context);
                final ArrayList<FriendRecord> records = db.getFriends();
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        mRecords = records;
                        notifyDataSetChanged();
                    }
                });
            }
        });
    }
}
