package xyz.zood.george;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

class LibrariesAdapter extends RecyclerView.Adapter<LibrariesAdapter.LibraryListItemViewHolder> {

    @NonNull
    private final Pair<String, Integer>[] items;
    private final LibrariesAdapterListener listener;

    LibrariesAdapter(LibrariesAdapterListener listener) {
        this.listener = listener;
        //noinspection unchecked
        items = new Pair[]{
                new Pair<>("Android Image Cropper", R.raw.apache2),
                new Pair<>("Android SDK", R.raw.apache2),
                new Pair<>("AndroidX Activity", R.raw.apache2),
                new Pair<>("AndroidX Appcompat", R.raw.apache2),
                new Pair<>("AndroidX ConstraintLayout", R.raw.apache2),
                new Pair<>("AndroidX Core", R.raw.apache2),
                new Pair<>("AndroidX Dynamicanimation", R.raw.apache2),
                new Pair<>("AndroidX Lifecycle Extensions", R.raw.apache2),
                new Pair<>("AndroidX ViewPager2", R.raw.apache2),
                new Pair<>("AndroidX Work", R.raw.apache2),
                new Pair<>("Firebase Messaging", R.raw.apache2),
                new Pair<>("Guava: Google Core Libraries for Java", R.raw.apache2),
                new Pair<>("gson", R.raw.apache2),
                new Pair<>("MapLibre Native", R.raw.bsd2clause),
                new Pair<>("Material Components for Android", R.raw.apache2),
                new Pair<>("picasso", R.raw.apache2),
                new Pair<>("Retrofit", R.raw.apache2),
        };

        setHasStableIds(true);
    }

    @NonNull
    @Override
    public LibraryListItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(R.layout.library_list_item, parent, false);

        return new LibraryListItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryListItemViewHolder holder, int position) {
        holder.textView.setText(items[position].first);
        if (position < items.length-1) {
            holder.divider.setVisibility(View.VISIBLE);
        } else {
            holder.divider.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onLibraryItemSelected(items[position]);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.length;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class LibraryListItemViewHolder extends RecyclerView.ViewHolder {

        private final View divider;
        private final MaterialTextView textView;

        public LibraryListItemViewHolder(@NonNull View itemView) {
            super(itemView);
            this.textView = itemView.findViewById(R.id.name);
            this.divider = itemView.findViewById(R.id.divider);
        }

    }

    interface LibrariesAdapterListener {
        void onLibraryItemSelected(Pair<String, Integer> libItem);
    }
}
