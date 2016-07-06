package edu.stanford.thingengine.engine.ui;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.net.URL;

/**
 * Created by gcampagn on 7/6/16.
 */
public class LoadImageTask extends AsyncTask<String, Void, Drawable> {
    private final ImageView view;

    public LoadImageTask(ImageView view) {
        this.view = view;
    }

    @Override
    protected Drawable doInBackground(String... params) {
        try {
            URL thumb_u = new URL(params[0]);
            return Drawable.createFromStream(thumb_u.openStream(), "src");
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
