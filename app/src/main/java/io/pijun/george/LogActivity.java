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

import io.pijun.george.service.LocationListenerService;

public class LogActivity extends AppCompatActivity implements Toolbar.OnMenuItemClickListener {

    public static Intent newIntent(@NonNull Context context) {
        return new Intent(context, LogActivity.class);
    }

    @Override
    @UiThread
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
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
        } else if (id == R.id.log_lls_stack) {
            StackTraceElement[] stackTrace = LocationListenerService.sServiceLooper.getThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append("LLS stack trace\n");
            for (StackTraceElement ste : stackTrace) {
                sb.append(ste.getClassName()).
                        append('.').
                        append(ste.getMethodName()).
                        append('(').
                        append(ste.getFileName()).
                        append(':').
                        append(ste.getLineNumber()).
                        append(')').
                        append('\n');
            }
            L.i(sb.toString());
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
            int val;
            while ((val = bis.read()) != -1) {
                sw.write(val);
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
        TextView tv = (TextView) findViewById(R.id.log_contents);
        tv.setText(logContents);
    }
}
