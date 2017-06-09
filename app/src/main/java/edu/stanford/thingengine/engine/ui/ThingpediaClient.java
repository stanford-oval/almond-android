package edu.stanford.thingengine.engine.ui;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Created by gcampagn on 6/27/16.
 */
public class ThingpediaClient {
    private static final String THINGPEDIA_URL = "https://thingengine.stanford.edu/thingpedia";

    private final Context ctx;

    public ThingpediaClient(Context ctx) {
        this.ctx = ctx;
    }

    private String getDeveloperKey() {
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("thingengine", Context.MODE_PRIVATE);
        JSONTokener jsonTokener = new JSONTokener(sharedPreferences.getString("developer-key", "null"));
        try {
            return jsonTokener.nextValue().toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public Object runSimpleRequest(String urlString) throws IOException, JSONException {
        String developerKey = getDeveloperKey();

        if (developerKey != null && developerKey.length() > 0)
            urlString += "&developer_key=" + developerKey;

        URL url = new URL(THINGPEDIA_URL + urlString);
        URLConnection connection = url.openConnection();
        try (Reader stream = new InputStreamReader(connection.getInputStream())) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read = stream.read(buffer);
            while (read > 0) {
                builder.append(buffer, 0, read);
                read = stream.read(buffer);
            }
            JSONTokener jsonTokener = new JSONTokener(builder.toString());
            return jsonTokener.nextValue();
        }
    }

    public JSONArray getDeviceFactories(String _class) throws IOException, JSONException {
        return (JSONArray)runSimpleRequest("/api/devices?class=" + _class);
    }

    public JSONArray getExamplesByKinds(String _kind) throws IOException, JSONException {
        String locale = Locale.getDefault().toString();
        JSONArray examples = (JSONArray)runSimpleRequest("/api/examples/by-kinds/" + _kind +
                "?locale=" + locale + "&base=1");
        // in case that no example is translated, return the english examples
        if (examples.length() == 0 && !locale.startsWith("en")) {
            examples = (JSONArray) runSimpleRequest("/api/examples/by-kinds/" + _kind +
                    "?locale=en-US&base=1");
        }
        return examples;
    }

    public JSONArray getExamplesByKey(String key) throws IOException, JSONException {
        String locale = Locale.getDefault().toString();
        JSONArray examples = (JSONArray)runSimpleRequest("/api/examples/?key=" + URLEncoder.encode(key, "UTF-8") +
                "&locale=" + locale + "&base=1");
        // in case that no example is translated, return the english examples
        if (examples.length() == 0 && !locale.startsWith("en")) {
            examples = (JSONArray) runSimpleRequest("/api/examples/by-kinds/" + URLEncoder.encode(key, "UTF-8") +
                    "?locale=en-US&base=1");
        }
        return examples;
    }
}
