package io.pijun.george;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import io.pijun.george.databinding.ActivityAvatarPickerBinding;

public class AvatarPickerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityAvatarPickerBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_avatar_picker);
        GridLayoutManager glm = new GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false);
        binding.avatarList.setLayoutManager(glm);
        binding.avatarList.setHasFixedSize(true);
    }

    private static class AvatarOptionsAdapter extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return null;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        }

        @Override
        public int getItemCount() {
            return 0;
        }
    }
}
