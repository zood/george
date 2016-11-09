package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.AnyThread;
import android.support.annotation.UiThread;
import android.support.v4.util.LongSparseArray;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.RequestRecord;
import io.pijun.george.models.UserRecord;

class FriendsAdapter extends RecyclerView.Adapter {

    private ArrayList<FriendRecord> mFriends = new ArrayList<>();
    private ArrayList<RequestRecord> mRequests = new ArrayList<>();
    private ArrayList<FriendItem> mItems = new ArrayList<>();
    private LongSparseArray<UserRecord> mCachedUsers = new LongSparseArray<>();
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
        void onApproveFriendRequest(long requestId);
        void onRejectFriendRequest(long requestId);
    }

    private static class FriendComparator implements Comparator<FriendRecord> {
        @Override
        public int compare(FriendRecord l, FriendRecord r) {
            return l.user.username.compareTo(r.user.username);
        }
    }

    private static class RequestComparator implements Comparator<RequestRecord> {
        @Override
        public int compare(RequestRecord l, RequestRecord r) {
            return (int)(l.sentDate - r.sentDate);
        }
    }

    FriendsAdapter(Context context) {
        reloadFriends(context);
//        setHasStableIds(true);
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
                    long position = (long) holder.shareButton.getTag();
                    onApproveRequestAction((int)position);
                }
            });
            holder.noButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    long position = (long) holder.noButton.getTag();
                    onRejectRequestAction((int)position);
                }
            });
            return holder;
        }

        throw new RuntimeException("Unknown view type");
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        FriendItemViewHolder h = (FriendItemViewHolder) holder;
        FriendItem item = mItems.get(position);
        Context context = h.profile.getContext();

        if (item.viewType == R.layout.friend_item) {
            FriendRecord friend = mFriends.get((int)item.id);
            h.profile.show(friend.user.username);
            h.username.setText(friend.user.username);
            h.location.setText("3682 Sunset Knolls Dr. (not real)");
        } else if (item.viewType == R.layout.friend_request_item) {
            RequestRecord request = mRequests.get((int) item.id);
            UserRecord user = mCachedUsers.get(request.userId);
            h.profile.show(user.username);
            h.username.setText(user.username);
            h.location.setText("gotta do something here");
            String msg = context.getString(R.string.share_your_location_with_msg, user.username);
            h.sharePrompt.setText(msg);
            h.shareButton.setTag(item.id);
            h.noButton.setTag(item.id);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    /*
    @Override
    public long getItemId(int position) {
        return mItems.get(position).id;
    }
    */

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
                final ArrayList<FriendRecord> friends = db.getFriends();
                final ArrayList<RequestRecord> incomingRequests = db.getIncomingRequests(true);
                final ArrayList<FriendItem> items = new ArrayList<>(friends.size() + incomingRequests.size());
                Collections.sort(incomingRequests, new RequestComparator());
                for (int i=0; i<incomingRequests.size(); i++) {
                    items.add(new FriendItem(R.layout.friend_request_item, i));
                    // cache the user's record
                    RequestRecord rr = incomingRequests.get(i);
                    UserRecord user = db.getUserById(rr.userId);
                    mCachedUsers.put(rr.userId, user);
                }
                Collections.sort(friends, new FriendComparator());
                for (int i=0; i<friends.size(); i++) {
                    items.add(new FriendItem(R.layout.friend_item, i));
                }
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        mFriends = friends;
                        mRequests = incomingRequests;
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
        if (position < 0 || position >= mRequests.size()) {
            L.i("onApproveRequestAction - invalid request pos: " + position);
            return;
        }

        RequestRecord request = mRequests.get(position);
        mListener.onApproveFriendRequest(request.userId);
    }

    @UiThread
    private void onRejectRequestAction(int position) {
        if (mListener == null) {
            return;
        }
        if (position < 0 || position >= mRequests.size()) {
            L.i("onRejectRequestAction - invalid request pos: " + position);
            return;
        }

        RequestRecord request = mRequests.get(position);
        mListener.onRejectFriendRequest(request.userId);
    }

    @UiThread
    void setListener(FriendsAdapterListener l) {
        mListener = l;
    }
}
