package com.filestack.android;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.support.v4.content.LocalBroadcastManager;
import com.filestack.android.internal.UploadService;

import com.filestack.FileLink;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */

interface UploadProgressListener {
    void onProgressUpdate(int progress, int currentFile, int maxFiles);
}

public class UploadProgressActivity extends AppCompatActivity implements UploadProgressListener {
    private View mContentView;
    private View mControlsView;
    private boolean mVisible;
    private Button mCancelButton;
    private ProgressBar mProgressBar;
    private TextView mProgressLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        setContentView(R.layout.activity_upload_progress);
        getSupportActionBar().setTitle(R.string.filestack__title_upload);
        mVisible = true;
        mControlsView =  findViewById(R.id.fullscreen_content_controls);
        mCancelButton = findViewById(R.id.cancel_button);
        mProgressBar = findViewById(R.id.progress_bar);
        mProgressBar.setProgress(0);

        mProgressLabel = findViewById(R.id.progress_label);
//        mContentView =  findViewById(R.id.fullscreen_content);

        // Set up the user interaction to manually show or hide the system UI.
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onFinish();
            }
        });

        onProgressUpdate(0, 0, intent.getIntExtra("uploadMaxFiles", 0));

        IntentFilter intentFilter = new IntentFilter(FsConstants.BROADCAST_UPLOAD);
        UploadStatusReceiver receiver = new UploadStatusReceiver(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    private void onFinish() {
//        setResult("", 0);
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, UploadService.class));
    }

    @Override
    public void onProgressUpdate(int progress, int currentFile, int maxFiles) {
        mProgressBar.setProgress(progress);
        String progressLabel = String.format("Uploading files: %d/%d", currentFile, maxFiles);
        mProgressLabel.setText(progressLabel);
    }

    public class UploadStatusReceiver extends BroadcastReceiver {
        private static final String TAG = "UploadStatusReceiver";

        UploadProgressListener listener;

        public UploadStatusReceiver(UploadProgressListener listener) {
            super();
            this.listener = listener;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            int maxFiles = intent.getIntExtra("uploadMaxFiles", 0);
            int currentFile = intent.getIntExtra("uploadCurrentFile", 0);
            int progress = intent.getIntExtra("uploadProgress", 0);
            listener.onProgressUpdate(progress, currentFile, maxFiles);
        }
    }
}