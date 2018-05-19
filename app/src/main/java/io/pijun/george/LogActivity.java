package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class LogActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {

    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, LogActivity.class);
    }

    @Override
    @UiThread
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.activity_log);
        toolbar.setOnMenuItemClickListener(this);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                loadLogs();
            }
        });
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.delete_log) {
            L.resetLog(this);
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    loadLogs();
                }
            });
            return true;
        }

        return false;
    }

    @WorkerThread
    private void loadLogs() {
        LogAdapter la = new LogAdapter(this);
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                setLogAdapter(la);
            }
        });
    }

    private void setLogAdapter(LogAdapter la) {
        RecyclerView list = findViewById(R.id.log_list);
        if (list != null) {
            list.setAdapter(la);
        }
    }

    static class LogItemViewHoder extends RecyclerView.ViewHolder {

        private final TextView textView;

        LogItemViewHoder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }

    static class LogAdapter extends RecyclerView.Adapter<LogItemViewHoder> {

        ArrayList<CharSequence> lines = new ArrayList<>();

        @WorkerThread
        LogAdapter(@NonNull Context ctx) {
            try {
                FileInputStream stream = ctx.openFileInput(L.LOG_FILENAME);
                BufferedInputStream bis = new BufferedInputStream(stream);
                StringBuilder s = new StringBuilder();
                while (bis.available() > 0) {
                    int c = bis.read();
                    if (c == '\n') {
                        lines.add(s);
                        s = new StringBuilder();
                    } else {
                        s.append((char)c);
                    }
                }
            } catch (FileNotFoundException fnfe) {
                lines.add("Unable to open file stream\n" + fnfe.getLocalizedMessage());
            } catch (IOException ioe) {
                lines.add("Unable to read from log\n" + ioe.getLocalizedMessage());
            }
        }

        @NonNull
        @Override
        public LogItemViewHoder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextIsSelectable(true);
            return new LogItemViewHoder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull LogItemViewHoder holder, int position) {
            holder.textView.setText(lines.get(position));
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }
    }
}
