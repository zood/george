package io.pijun.george;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

public class AvatarsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private int mItemCount = 12;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == R.layout.avatar_container_margin) {
            View view = inflater.inflate(R.layout.avatar_container_margin, parent, false);
            return new AvatarContainerMarginViewHolder(view);
        } else if (viewType == R.layout.avatar_preview) {
            View view = inflater.inflate(R.layout.avatar_preview, parent, false);
            return new AvatarViewHolder(view);
        }

        throw new IllegalArgumentException("Unknown view type");
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 || position == mItemCount-1) {
            return R.layout.avatar_container_margin;
        }

        return R.layout.avatar_preview;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
//        Drawable clooney = holder.button.getContext().getResources().getDrawable(R.drawable.george_clooney, null);
        if (holder instanceof AvatarViewHolder) {
            AvatarViewHolder h = (AvatarViewHolder) holder;
            Resources rsrcs = h.button.getResources();
            Bitmap clooney = BitmapFactory.decodeResource(rsrcs, R.drawable.george_clooney);
            L.i("clooney: " + clooney.getWidth() + ", height: " + clooney.getHeight());
            RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(rsrcs, clooney);
            rounded.setCornerRadius(rsrcs.getDimension(R.dimen.twentyEight));
            rounded.setCircular(true);
            h.button.setImageDrawable(rounded);
        }
    }

    @Override
    public int getItemCount() {
        return mItemCount;
    }

    private static class AvatarContainerMarginViewHolder extends RecyclerView.ViewHolder {
        AvatarContainerMarginViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class AvatarViewHolder extends RecyclerView.ViewHolder {
        final ImageButton button;

        AvatarViewHolder(View itemView) {
            super(itemView);

            button = (ImageButton) itemView;
        }
    }
}
