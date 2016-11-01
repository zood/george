package io.pijun.george;

import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.pijun.george.models.FriendRecord;

class FriendsAdapter extends RecyclerView.Adapter {

    private ArrayList<FriendRecord> mRecords = new ArrayList<>();
    private ArrayList<FriendItem> mItems = new ArrayList<>();
    private FriendsAdapterListener mListener;

    private static class FriendItem {
        final int viewType;
        final long id;

        FriendItem(int viewType, long id) {
            this.viewType = viewType;
            this.id = id;
        }
    }

    interface FriendsAdapterListener {
        void onApproveFriendRequest(byte[] userId);
        void onRejectFriendRequest(byte[] userId);
    }

    private static class FriendComparator implements Comparator<FriendRecord> {

        @Override
        public int compare(FriendRecord l, FriendRecord r) {
            if (l.shareRequestedOfMe && !r.shareRequestedOfMe) {
                return -1;
            } else if (r.shareRequestedOfMe && !l.shareRequestedOfMe) {
                return 1;
            }

            return (int)(l.id - r.id);
        }
    }

    FriendsAdapter(Context context) {
        reloadFriends(context);
        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == R.layout.friend_item) {
            View view = inflater.inflate(R.layout.friend_item, parent, false);
            return new FriendItemViewHolder(view);
        } else if (viewType == R.layout.friend_request_item) {
            View view = inflater.inflate(R.layout.friend_request_item, parent, false);
            final FriendItemViewHolder holder = new FriendItemViewHolder(view);
            holder.shareButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = (int) holder.shareButton.getTag();
                    onApproveRequestAction(position);
                }
            });
            holder.noButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = (int) holder.noButton.getTag();
                    onRejectRequestAction(position);
                }
            });
            return holder;
        }

        throw new RuntimeException("Unknown view type");
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        FriendItemViewHolder h = (FriendItemViewHolder) holder;
        FriendRecord record = mRecords.get(position);
        FriendItem item = mItems.get(position);
        Context context = h.profile.getContext();
        h.profile.show("arashpayan");
        h.username.setText(record.username);
        if (item.viewType == R.layout.friend_item) {
            if (record.receivingBoxId == null) {
                if (record.friendRequestSendDate != null) {
                    h.location.setText("Waiting for friend request response");
                } else {
                    h.location.setText("Not sharing location with you");
                }
            } else {
                h.location.setText("3682 Sunset Knolls Dr.");
            }
        } else if (item.viewType == R.layout.friend_request_item) {
            if (record.receivingBoxId == null) {
                h.location.setText("Chose not to share location with you");
            } else {
                h.location.setText("1652 Valecroft Ave.");
            }
            String msg = context.getString(R.string.share_your_location_with_msg, record.username);
            h.sharePrompt.setText(msg);
            h.shareButton.setTag(position);
            h.noButton.setTag(position);
        }
    }

    @Override
    public int getItemCount() {
        return mRecords.size();
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).viewType;
    }

    @AnyThread
    void reloadFriends(final Context context) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                DB db = DB.get(context);
                final ArrayList<FriendRecord> records = db.getFriends();
                final ArrayList<FriendItem> items = new ArrayList<>(records.size());
                for (FriendRecord fr : records) {
                    if (fr.shareRequestedOfMe) {
                        items.add(new FriendItem(R.layout.friend_request_item, fr.id));
                    } else {
                        items.add(new FriendItem(R.layout.friend_item, fr.id));
                    }
                }
                Collections.sort(records, new FriendComparator());
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        mRecords = records;
                        mItems = items;
                        notifyDataSetChanged();
                    }
                });
            }
        });
    }

    @UiThread
    private void onApproveRequestAction(int position) {
        if (mListener == null) {
            return;
        }
        if (position < 0 || position >= mItems.size()) {
            L.i("onApproveRequestAction - invalid friend pos: " + position);
            return;
        }

        FriendRecord record = mRecords.get(position);
        mListener.onApproveFriendRequest(record.userId);
    }

    @UiThread
    private void onRejectRequestAction(int position) {
        if (mListener == null) {
            return;
        }
        if (position < 0 || position >= mItems.size()) {
            L.i("onRejectRequestAction - invalid friend pos: " + position);
            return;
        }

        FriendRecord record = mRecords.get(position);
        mListener.onRejectFriendRequest(record.userId);
    }

    @UiThread
    void setListener(FriendsAdapterListener l) {
        mListener = l;
    }
}
