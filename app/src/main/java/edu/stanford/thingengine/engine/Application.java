package edu.stanford.thingengine.engine;

import android.net.http.HttpResponseCache;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Created by gcampagn on 7/6/16.
 */
public class Application extends android.app.Application {
    public static final String LOG_TAG = "thingengine";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            HttpResponseCache.install(new File(getCacheDir() + "/http"), httpCacheSize);
        } catch(IOException e) {
            Log.w(LOG_TAG, "Failed to enable HTTP cache, application will run slower", e);
        }
    }
}
