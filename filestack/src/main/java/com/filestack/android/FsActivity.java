package com.filestack.android;

import static android.app.Activity.RESULT_FIRST_USER;
import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

import static com.filestack.android.internal.Util.CAMERA_PICKER_ACTIVITY_REQUEST_ID;
import static com.filestack.android.internal.Util.DEVICE_PICKER_ACTIVITY_REQUEST_ID;
import static com.filestack.android.internal.Util.UPLOAD_PROGRESS_ACTIVITY_REQUEST_ID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MenuCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ImageViewCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.filestack.CloudResponse;
import com.filestack.Config;
import com.filestack.Sources;
import com.filestack.StorageOptions;
import com.filestack.android.internal.AdvancedCameraActivity;
import com.filestack.android.internal.BackButtonListener;
import com.filestack.android.internal.CameraFragment;
import com.filestack.android.internal.CloudAuthFragment;
import com.filestack.android.internal.CloudListFragment;
import com.filestack.android.internal.LocalFilesFragment;
import com.filestack.android.internal.SelectionSaver;
import com.filestack.android.internal.SourceInfo;
import com.filestack.android.internal.UploadService;
import com.filestack.android.internal.Util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.CompletableObserver;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

/**
 * UI to select and upload files from local and cloud sources.
 * <p>
 * This class should be launched through the creation and sending of an {{@link Intent}}.
 * Options are set by passing values to {@link Intent#putExtra(String, String)}.
 * The keys and descriptions for these options are defined in {{@link FsConstants}}.
 * <p>
 * There are two types of results from this activity, the files a user selects ({{@link Selection}})
 * and the metadata returned when these selections are uploaded ({{@link com.filestack.FileLink}}).
 * Automatic uploads can be disabled, in which case you will not receive any of the latter.
 * <p>
 * User selections are returned as an {{@link ArrayList}} of {{@link Selection}} objects to
 * {{@link android.app.Activity#onActivityResult(int, int, Intent)}}. To receive upload metadata,
 * you must define and register a {{@link android.content.BroadcastReceiver}}. The corresponding
 * {{@link android.content.IntentFilter}} must be created to catch
 * {{@link FsConstants#BROADCAST_UPLOAD}}. Upload metadata is returned as
 * {{@link com.filestack.FileLink}} objects passed to
 * {{@link android.content.BroadcastReceiver#onReceive(Context, Intent)}}. The key strings needed to
 * pull results from intents are defined in {{@link FsConstants}}.
 * <p>
 * The intent and broadcast mechanisms, and keys defined in {{@link FsConstants}}, are the contract
 * for this class. The actual code of this class should be considered internal implementation.
 */
public class FsActivity extends AppCompatActivity implements
        SingleObserver<CloudResponse>, CompletableObserver, SelectionSaver.Listener,
        NavigationView.OnNavigationItemSelectedListener, SourceSelectionListener {

    private static final String PREF_SESSION_TOKEN = "sessionToken";
    private static final String STATE_SELECTED_SOURCE = "selectedSource";
    private static final String STATE_SHOULD_CHECK_AUTH = "shouldCheckAuth";
    private static final String TAG = "FsActivity";

    private BackButtonListener backListener;
    private DrawerLayout drawer;
    private String selectedSource;
    private boolean shouldCheckAuth;
    private boolean isUploadingStarted = false;
    private NavigationView nav;

    private boolean allowMultipleFiles;
    private boolean showVersionInfo;

    private Theme theme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

//        setContentView(R.layout.filestack__activity_filestack);
        setContentView(R.layout.filestack__sources_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        theme = intent.getParcelableExtra(FsConstants.EXTRA_THEME);
        if (theme == null) {
            theme = Theme.defaultTheme();
        }

        getSupportActionBar().setTitle(theme.getTitle());
        toolbar.setTitleTextColor(theme.getBackgroundColor());
        toolbar.setSubtitleTextColor(ColorUtils.setAlphaComponent(theme.getBackgroundColor(), 220));
        toolbar.setBackgroundColor(theme.getAccentColor());
        List<String> sources = (List<String>) intent.getSerializableExtra(FsConstants.EXTRA_SOURCES);
        if (sources == null) {
            sources = Util.getDefaultSources();
        }

        allowMultipleFiles = intent.getBooleanExtra(FsConstants.EXTRA_ALLOW_MULTIPLE_FILES, true);
        showVersionInfo = intent.getBooleanExtra(FsConstants.EXTRA_DISPLAY_VERSION_INFORMATION, true);

//        String[] mimeTypes = intent.getStringArrayExtra(FsConstants.EXTRA_MIME_TYPES);
//        if (mimeTypes != null && sources.contains(Sources.CAMERA)) {
//            if (!Util.mimeAllowed(mimeTypes, "image/jpeg") && !Util.mimeAllowed(mimeTypes, "video/mp4")) {
//                sources.remove(Sources.CAMERA);
//                Log.w(TAG, "Hiding camera since neither image/jpeg nor video/mp4 MIME type is allowed");
//            }
//        }

        ArrayList<SourceInfo> selectedSources = new ArrayList<>();
        for (String source : sources) {
            int id = Util.getSourceIntId(source);
            SourceInfo info = Util.getSourceInfo(source);
            selectedSources.add(info);
        }
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        SourceListAdapter adapter = new SourceListAdapter(selectedSources, this, theme);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        Config config = (Config) intent.getSerializableExtra(FsConstants.EXTRA_CONFIG);
        String sessionToken = preferences.getString(PREF_SESSION_TOKEN, null);
        Util.initializeClient(config, sessionToken);
        Util.getSelectionSaver().clear();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        String sessionToken = Util.getClient().getSessionToken();
        preferences.edit().putString(PREF_SESSION_TOKEN, sessionToken).apply();
        Util.getSelectionSaver().setItemChangeListener(null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(STATE_SELECTED_SOURCE, selectedSource);
        outState.putBoolean(STATE_SHOULD_CHECK_AUTH, shouldCheckAuth);
    }

    @Override
    public void onBackPressed() {
//        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
//            drawer.closeDrawer(GravityCompat.START);
//        } else if (backListener == null || !backListener.onBackPressed()) {
//            super.onBackPressed();
//        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void onComplete() {}

    @Override
    public void onError(Throwable e) {
        e.printStackTrace();
        // TODO Error handling
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == DEVICE_PICKER_ACTIVITY_REQUEST_ID && resultCode == RESULT_OK) {
            ClipData clipData = resultData.getClipData();
            ArrayList<Uri> uris = new ArrayList<>();

            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
            } else {
                uris.add(resultData.getData());
            }

            for (Uri uri : uris) {
                Selection selection = Util.processUri(uri, getContentResolver());
                Util.getSelectionSaver().toggleItem(selection);
            }
            uploadSelections(Util.getSelectionSaver().getItems());
        }

        if (requestCode == CAMERA_PICKER_ACTIVITY_REQUEST_ID && resultCode == RESULT_OK) {
            uploadSelections(Util.getSelectionSaver().getItems());
        }

        if (requestCode == UPLOAD_PROGRESS_ACTIVITY_REQUEST_ID) {
            Intent data = new Intent();
            data.putExtra(FsConstants.EXTRA_SELECTION_LIST, Util.getSelectionSaver().getItems());
            setResult(RESULT_OK, data);
            finish();
        }
    }

    @Override
    public void onEmptyChanged(boolean isEmpty) {}

    @Override
    public void onSubscribe(Disposable d) {
    }

    @Override
    public void onSuccess(CloudResponse contents) {
        String authUrl = contents.getAuthUrl();

        // TODO Switching source views shouldn't depend on a network request

//        if (authUrl != null) {
//            shouldCheckAuth = true;
//            CloudAuthFragment fragment = CloudAuthFragment.create(selectedSource, authUrl, theme);
//            showFragment(fragment);
//        } else {
//            shouldCheckAuth = false;
//            CloudListFragment fragment = CloudListFragment.create(selectedSource, allowMultipleFiles, theme);
//            showFragment(fragment);
//        }
    }

    private void uploadSelections(ArrayList<Selection> selections) {
        Intent activityIntent = getIntent();
        boolean autoUpload = activityIntent.getBooleanExtra(FsConstants.EXTRA_AUTO_UPLOAD, true);
        if (autoUpload) {
            StorageOptions storeOpts = (StorageOptions) activityIntent
                    .getSerializableExtra(FsConstants.EXTRA_STORE_OPTS);
            Intent uploadIntent = new Intent(this, UploadService.class);
            uploadIntent.putExtra(FsConstants.EXTRA_STORE_OPTS, storeOpts);
            uploadIntent.putExtra(FsConstants.EXTRA_SELECTION_LIST, selections);
            ContextCompat.startForegroundService(this, uploadIntent);
        }

        Intent uploadProgressIntent = new Intent(this, UploadProgressActivity.class);
        uploadProgressIntent.putExtra("uploadMaxFiles", selections.size());
        startActivityForResult(uploadProgressIntent, UPLOAD_PROGRESS_ACTIVITY_REQUEST_ID);
    }

    @Override
    public void onSourceSelected(String id) {
        switch (id) {
            case Sources.CAMERA:
                startCameraPicker();
                break;
            case Sources.DEVICE:
                startFilePicker();
                break;
            default:
                break;
        }
    }
    private static final String TYPE_PHOTO = "photo";
    private static final String TYPE_VIDEO = "video";
    private static final String PREF_PATH = "path";
    private static final String PREF_NAME= "name";
    private static final String ARG_THEME = "theme";

    private void startCameraPicker() {
        Intent cameraIntent = new Intent(this, AdvancedCameraActivity.class);
        startActivityForResult(cameraIntent, CAMERA_PICKER_ACTIVITY_REQUEST_ID);
//        int id = view.getId();
//        Intent cameraIntent = null;
//
//        cameraIntent = createCameraIntent(TYPE_VIDEO);
//
//        startActivityForResult(cameraIntent, CAMERA_PICKER_ACTIVITY_REQUEST_ID);
//        Intent takeVideoFromCameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        takeVideoFromCameraIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
//        takeVideoFromCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, getOutputMediaFile(MEDIA_TYPE_IMAGE));
//        takeVideoFromCameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        startActivityForResult(takeVideoFromCameraIntent, CAMERA_PICKER_ACTIVITY_REQUEST_ID);
    }

    public Uri getOutputMediaFile(int type)
    {
        if(Environment.getExternalStorageState() != null) {
            File mediaStorageDir = Util.storageDir();
            /* Create a media file name */
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            File mediaFile;
            if (type == MEDIA_TYPE_IMAGE) {
                mediaFile = new File(mediaStorageDir.getPath(), "PHOTO_"+ timeStamp + ".jpeg");
            } else {
                mediaFile = new File(mediaStorageDir.getPath(), "VID_"+ timeStamp + ".mp4");
            }
            return FileProvider.getUriForFile(this, getPackageName() + ".provider", mediaFile);
        }
        return null;
    }

//    private Intent createCameraIntent(String source) {
//        Intent intent = null;
//        File file = null;
//
//        SharedPreferences prefs = context.getSharedPreferences(getClass().getName(), MODE_PRIVATE);
//
//        try {
//            switch (source) {
//                case TYPE_PHOTO:
//                    intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//                    file = Util.createPictureFile(context);
//                    break;
//                case TYPE_VIDEO:
//                    intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
//                    file = Util.createMovieFile(context);
//                    break;
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        if (file != null) {
//            String path = file.getAbsolutePath();
//            String name = file.getName();
//            prefs.edit().putString(PREF_PATH, path).putString(PREF_NAME, name).apply();
//            Uri uri = Util.getUriForInternalMedia(getContext(), file);
//            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
//        }
//
//        return intent;
//    }

    private void startFilePicker() {
        final Intent intent;
        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultipleFiles);
        intent.setType("*/*");

        Intent launchIntent = getIntent();
        String[] mimeTypes = launchIntent.getStringArrayExtra(FsConstants.EXTRA_MIME_TYPES);
        if (mimeTypes != null) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        startActivityForResult(intent, DEVICE_PICKER_ACTIVITY_REQUEST_ID);
    }
}
