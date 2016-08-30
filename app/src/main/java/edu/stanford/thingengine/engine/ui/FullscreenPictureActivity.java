package edu.stanford.thingengine.engine.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.koushikdutta.ion.Ion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import edu.stanford.thingengine.engine.ContentUtils;
import edu.stanford.thingengine.engine.R;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenPictureActivity extends Activity {
    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private Uri url;
    private boolean mDownloadRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen_picture);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        Button shareBtn = (Button) findViewById(R.id.btn_share);
        shareBtn.setOnTouchListener(mDelayHideTouchListener);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, url);
                intent.setType("image/*");
                startActivity(Intent.createChooser(intent, getResources().getText(R.string.btn_share)));
            }
        });

        findViewById(R.id.btn_download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryDownload();
            }
        });
    }

    private void tryDownload() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED)
            doDownload();
        else
            requestPermission();
    }

    private void requestPermission() {
        mDownloadRequested = true;

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            Toast.makeText(this, R.string.download_permission_needed, Toast.LENGTH_LONG).show();

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
    }

    private void doDownload() {
        if (url == null)
            return;
        new DownloadImageTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url.toString());
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String url = params[0];
            Uri parsed = Uri.parse(url);

            String filename = parsed.getLastPathSegment();
            // remove extension
            if (filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".gif"))
                filename = filename.substring(0, filename.length() - 4);
            else if (filename.endsWith(".jpeg"))
                filename = filename.substring(0, filename.length() - 5);
            if (filename.length() < 4)
                filename = "image";

            try {
                Pair<InputStream, String> pair = ContentUtils.readUrl(FullscreenPictureActivity.this, url);
                try (InputStream stream = pair.first) {
                    String mimeType = pair.second;

                    String extension = "";
                    if ("image/png".equals(mimeType))
                        extension = ".png";
                    if ("image/jpeg".equals(mimeType))
                        extension = ".jpeg";
                    if ("image/gif".equals(mimeType))
                        extension = ".gif";

                    // find where to save the picture
                    File[] dirs = getExternalMediaDirs();
                    File root = Environment.getExternalStorageDirectory();
                    File directory = new File(root, "Sabrina");
                    if (!directory.mkdir() && !directory.isDirectory())
                        throw new IOException("Failed to create app folder");

                    File dest = new File(directory, filename + extension);
                    int i = 0;
                    while (dest.exists())
                        dest = new File(directory, filename + String.format(Locale.getDefault(), "-%4d", ++i) + extension);

                    if (!dest.createNewFile())
                        throw new IOException("File already exists");
                    try (OutputStream output = new FileOutputStream(dest)) {
                        byte[] buffer = new byte[8192];
                        for (int read = stream.read(buffer); read > 0; read = stream.read(buffer))
                            output.write(buffer, 0, read);
                    }

                    return true;
                }
            } catch(IOException e) {
                Log.e(MainActivity.LOG_TAG, "Failed to save picture to gallery", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Toast.makeText(FullscreenPictureActivity.this, R.string.toast_downloaded, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(FullscreenPictureActivity.this, R.string.toast_download_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()))
            return;

        url = intent.getData();
        if (url == null)
            return;
        Ion.with(this).load(url.toString()).intoImageView((ImageView) findViewById(R.id.fullscreen_content));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button.
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_EXTERNAL_STORAGE)
            return;

        for (int i = 0; i < permissions.length; i++) {
            if (!permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                continue;

            if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                return;
        }

        if (!mDownloadRequested)
            return;

        doDownload();
        mDownloadRequested = false;
    }
}
