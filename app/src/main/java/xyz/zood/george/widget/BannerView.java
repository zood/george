package xyz.zood.george.widget;

import android.animation.LayoutTransition;
import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import io.pijun.george.R;

public class BannerView extends LinearLayout {

    private SparseArray<ItemHolder> items = new SparseArray<>();

    public BannerView(Context context) {
        super(context);
        init();
    }

    public BannerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BannerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(LinearLayout.VERTICAL);
        setLayoutTransition(new LayoutTransition());
    }

    @UiThread
    public void addItem(@NonNull String msg, @NonNull String action, int itemId, @NonNull ItemClickListener listener) {
        // Check if we already have a view for this item. If so, update it
        ItemHolder holder = items.get(itemId);
        if (holder != null) {
            holder.textView.setText(msg);
            holder.button.setText(action);
            holder.button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onBannerItemClick(itemId);
                }
            });
            return;
        }

        // we don't have the view, so inflate a new one and add it
        ConstraintLayout itemView = (ConstraintLayout) LayoutInflater.from(getContext()).inflate(R.layout.banner_item, this, false);
        holder = new ItemHolder(itemView, itemId);
        holder.textView.setText(msg);
        holder.button.setText(action);
        holder.button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onBannerItemClick(itemId);
            }
        });

        items.put(itemId, holder);
        addView(itemView, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @UiThread
    public void removeItem(int itemId) {
        int size = items.size();
        int i=0;
        while (i<size) {
            ItemHolder holder = items.valueAt(i);
            if (holder.id == itemId) {
                removeView(holder.view);
                items.delete(holder.id);
                break;
            }
            i++;
        }
    }

    public interface ItemClickListener {
        @UiThread
        void onBannerItemClick(int id);
    }

    private class ItemHolder {
        final private ConstraintLayout view;
        final int id;
        final TextView textView;
        final MaterialButton button;

        ItemHolder(@NonNull ConstraintLayout view, int id) {
            this.view = view;
            this.id = id;

            textView = view.findViewById(R.id.message);
            button = view.findViewById(R.id.button);
        }
    }

}
