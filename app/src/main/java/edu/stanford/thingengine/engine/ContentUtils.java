package edu.stanford.thingengine.engine;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by gcampagn on 7/22/16.
 */
public class ContentUtils {
    private ContentUtils() {}

    public static Pair<InputStream, String> readUrl(Context ctx, String url) throws IOException {
        Uri parsed = Uri.parse(url);
        String scheme = parsed.getScheme();
        if (scheme.equals(ContentResolver.SCHEME_CONTENT) ||
                scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE) ||
                scheme.equals(ContentResolver.SCHEME_FILE)) {
            ContentResolver resolver = ctx.getContentResolver();
            return new Pair<>(resolver.openInputStream(parsed), resolver.getType(parsed));
        } else {
            URL javaUrl = new URL(url);
            URLConnection connection = javaUrl.openConnection();
            String type = connection.getContentType();

            return new Pair<>(connection.getInputStream(), type);
        }
    }
}
