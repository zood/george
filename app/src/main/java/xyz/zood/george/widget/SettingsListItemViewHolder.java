package xyz.zood.george.widget;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

public class SettingsListItemViewHolder extends RecyclerView.ViewHolder {

    public final MaterialTextView textView;

    public SettingsListItemViewHolder(@NonNull View itemView) {
        super(itemView);

        this.textView = (MaterialTextView) itemView;
    }


}
