package com.filestack.android.internal;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.ServiceCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.filestack.FileLink;
import com.filestack.Progress;
import com.filestack.Sources;
import com.filestack.StorageOptions;
import com.filestack.android.DataHashMap;
import com.filestack.android.FsConstants;
import com.filestack.android.R;
import com.filestack.android.Selection;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;

/**
 * If the auto upload option is left enabled, a user's selections will be sent to this service
 * when {@link com.filestack.android.FsActivity} is closed (upload button is clicked). In this
 * service we upload files (or makes calls for cloud to cloud transfers) and send up notification
 * messages on the progress.
 * TODO Use async version of Java SDK upload so we can show incremental progress for large uploads
 */

public class UploadService extends Service {

    private static final String NOTIFY_CHANNEL_UPLOAD = "uploadsChannel";

    private Executor executor = Executors.newSingleThreadExecutor();
    Flowable<Progress<FileLink>> upload;

    private NotificationManager notificationManager;
    private int notificationId;
    private int errorNotificationId;

    private ArrayList<DataHashMap> response = new ArrayList<>();

    private int maxSize = 0;
    private double currentSize = 0;
    private int currentFile = 0;
    private int maxFiles = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationId = UUID.randomUUID().hashCode();
        errorNotificationId = notificationId + 1;

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final ArrayList<Selection> selections = intent.getParcelableArrayListExtra(FsConstants.EXTRA_SELECTION_LIST);
        StorageOptions storeOpts = (StorageOptions) intent.getSerializableExtra(FsConstants.EXTRA_STORE_OPTS);
        if (storeOpts == null) {
            storeOpts = new StorageOptions.Builder().build();
        }

        Notification serviceNotification =
                progressNotification(0, selections.size(), "").build();
        startForeground(notificationId, serviceNotification);

        final StorageOptions storageOptions = storeOpts;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                uploadFiles(selections, storageOptions);
                stopSelf();
            }
        });
        return START_STICKY;
    }

    private void uploadFiles(List<Selection> selections, StorageOptions storeOpts) {
        maxFiles = selections.size();
        maxSize = selections.size();
        currentFile = 0;
        currentSize = 0;
        sendBroadcastProgress(false);
        for (Selection item : selections) {
//            String name = item.getName();

//            sendProgressNotification(i, total, name);

            uploadAsync(item, storeOpts);
//            FileLink fileLink = upload(item, storeOpts);

            // If upload fails, decrease total count and show error notification
//            if (fileLink == null) {
//                sendErrorNotification(item.getName());
//                total--;
//            } else {
//                i++;
//            }
//
//            sendProgressNotification(i, total, name);
//            sendBroadcast(item, fileLink);
        }
    }

    private void uploadAsync(Selection selection, StorageOptions baseOptions) {
        System.out.println("start upload");
        try {
            String provider = selection.getProvider();
            String path = selection.getPath();
            Uri uri = selection.getUri();
            final int size = selection.getSize();
            final String name = selection.getName();
            final String mimeType = selection.getMimeType();
            StorageOptions options = baseOptions.newBuilder()
                    .filename(name)
                    .mimeType(mimeType)
                    .build();
            InputStream input = getContentResolver().openInputStream(uri);

            FileLink file = Util.getClient().uploadAsync(input, size, false, options).doOnNext(new Consumer<Progress<FileLink>>() {
                @Override
                public void accept(Progress<FileLink> progress) throws Exception {
                    System.out.printf("%f%% uploaded %s\n", progress.getPercent(), name);
                    currentSize = currentFile > 0 ? (currentFile - 1) + progress.getPercent() : progress.getPercent();
                    sendBroadcastProgress(false);
                }
            }).blockingLast().getData();

            DataHashMap data = new DataHashMap();
            data.put("container", file.getContainer());
            data.put("filename", file.getFilename());
            data.put("mimetype", file.getMimeType());
            data.put("size", file.getSize());
            data.put("url", file.getUrl());
            data.put("key", file.getKey());
            response.add(data);
            currentFile++;
            if (currentFile >= maxFiles) {
                sendBroadcastProgress(true);
            } else {
                sendBroadcastProgress(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private FileLink upload(Selection selection, StorageOptions baseOptions) {
        String provider = selection.getProvider();
        String path = selection.getPath();
        Uri uri = selection.getUri();
        int size = selection.getSize();
        String name = selection.getName();
        String mimeType = selection.getMimeType();

        StorageOptions options = baseOptions.newBuilder()
                .filename(name)
                .mimeType(mimeType)
                .build();

        try {
            switch (selection.getProvider()) {
                case Sources.CAMERA:
                    // TODO This should maybe be unified into an InputStream upload
                    return Util.getClient().upload(path, false, options);
                case Sources.DEVICE:
                    InputStream input = getContentResolver().openInputStream(uri);
                    return Util.getClient().upload(input, size, false, options);
                default:
                    return Util.getClient().storeCloudItem(provider, path, options);
            }
        } catch (Exception e) {
            // TODO Update after fixing synchronous versions of upload methods in Java SDK
            // Currently these are "block mode" observables and don't properly pass up exceptions
            // correctly among other issues
            e.printStackTrace();
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        CharSequence name = getString(R.string.filestack__notify_channel_upload_name);
        String description = getString(R.string.filestack__notify_channel_upload_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel channel =
                new NotificationChannel(NOTIFY_CHANNEL_UPLOAD, name, importance);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);
    }

    private void sendProgressNotification(int done, int total, String name) {
        if (total == 0) {
            notificationManager.cancel(notificationId);
            return;
        }
        NotificationCompat.Builder builder;
        if (total == done) {
            builder = new NotificationCompat.Builder(this, NOTIFY_CHANNEL_UPLOAD);
            builder.setContentTitle(String.format(Locale.getDefault(), "Uploaded %d files", done));
            builder.setSmallIcon(R.drawable.filestack__ic_menu_upload_done_white);
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        } else {
            builder = progressNotification(done, total, name);
        }
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        notificationManager.notify(notificationId, builder.build());
    }

    private NotificationCompat.Builder progressNotification(int done, int total, String currentFileName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFY_CHANNEL_UPLOAD);
        builder.setContentTitle(String.format(Locale.getDefault(), "Uploading %d/%d files", done, total));
        builder.setSmallIcon(R.drawable.filestack__ic_menu_upload_white);
        builder.setContentText(currentFileName);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setProgress(total, done, false);
        return builder;
    }

    private void sendErrorNotification(String name) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFY_CHANNEL_UPLOAD);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setContentTitle("Upload failed");
        builder.setContentText(name);
        builder.setSmallIcon(R.drawable.filestack__ic_menu_upload_fail_white);

        notificationManager.notify(errorNotificationId, builder.build());
    }

    private void sendBroadcastProgress(boolean finishedUpload) {
        Intent intent = new Intent(FsConstants.BROADCAST_UPLOAD);
        int progress = (int)(currentSize/maxSize * 100);
        intent.putExtra("uploadProgress", progress);
        intent.putExtra("uploadCurrentFile", currentFile);
        intent.putExtra("uploadMaxFiles", maxFiles);
        intent.putExtra("uploadFinished", finishedUpload);
        intent.putExtra("uploadResult", response);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendBroadcast(Selection selection, FileLink fileLink) {
        Intent intent = new Intent(FsConstants.BROADCAST_UPLOAD);
        intent.putExtra(FsConstants.EXTRA_SELECTION, selection);
        if (fileLink == null) {
            intent.putExtra(FsConstants.EXTRA_STATUS, FsConstants.STATUS_FAILED);
        } else {
            try {
                System.out.println(fileLink.getContent().toString());
                intent.putExtra(FsConstants.EXTRA_STATUS, FsConstants.STATUS_COMPLETE);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                System.out.println("failed");
            }

        }
        intent.putExtra(FsConstants.EXTRA_FILE_LINK, fileLink);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}
