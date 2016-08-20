package edu.stanford.thingengine.engine.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

import edu.stanford.thingengine.engine.ContentUtils;

/**
 * Created by gcampagn on 7/6/16.
 */
public class LoadImageTask extends AsyncTask<String, Void, Drawable> {
    private final Context ctx;
    private final ImageView view;

    public LoadImageTask(Context ctx, ImageView view) {
        this.ctx = ctx;
        this.view = view;
    }

    @Override
    protected Drawable doInBackground(String... params) {
        try {
            Pair<InputStream, String> pair = ContentUtils.readUrl(ctx, params[0]);
            try (InputStream stream = pair.first) {
                String mimeType = pair.second;
                return Drawable.createFromStream(stream, "src");
            }
        } catch (IOException | OutOfMemoryError e) {
            Log.e(MainActivity.LOG_TAG, "Failed to retrieve image from server", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(Drawable drawable) {
        if (drawable != null)
            view.setImageDrawable(drawable);
        else
            view.setImageResource(android.R.drawable.stat_notify_error);
    }
}
