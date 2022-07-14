package com.filestack.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.filestack.FileLink;

import java.util.ArrayList;
import java.util.List;

interface UploadProgressDelegate {
    void onComplete(ArrayList<FileLink> fileLink);
}

public class UploadProgressReceiver extends BroadcastReceiver {

    private UploadProgressDelegate receiver;
    private ArrayList<FileLink> fileList = new ArrayList<>();

    public UploadProgressReceiver(UploadProgressDelegate receiver) {
        super();
        this.receiver = receiver;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int maxFilesCount = intent.getIntExtra("maxFilesCount", 0);
        if (fileList.size() == maxFilesCount) {
            this.receiver.onComplete(fileList);
        }
    }

}
