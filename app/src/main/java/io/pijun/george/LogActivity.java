package io.pijun.george;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;

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
            setLogContents("");
            return true;
        }

        return false;
    }

    @WorkerThread
    private void loadLogs() {
        String logText;
        try {
            FileInputStream stream = openFileInput(L.LOG_FILENAME);
            BufferedInputStream bis = new BufferedInputStream(stream);
            StringWriter sw = new StringWriter();
            byte[] buf = new byte[8192];
            while (bis.available() > 0) {
                int numRead = bis.read(buf);
                String str = new String(buf, 0, numRead);
                sw.write(str);
            }
            logText = sw.toString();
        } catch (FileNotFoundException fnfe) {
            logText = "Unable to open file stream\n" + fnfe.getLocalizedMessage();
        } catch (IOException ioe) {
            logText = "Unable to read from log\n" + ioe.getLocalizedMessage();
        }
        final String logContents = logText;
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                setLogContents(logContents);
            }
        });
    }

    @UiThread
    private void setLogContents(String logContents) {
        TextView tv = findViewById(R.id.log_contents);
        tv.setText(logContents);
    }
}
