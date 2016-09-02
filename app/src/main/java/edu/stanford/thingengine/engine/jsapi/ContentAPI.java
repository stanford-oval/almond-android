package edu.stanford.thingengine.engine.jsapi;

import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.RejectedExecutionException;

import edu.stanford.thingengine.engine.ContentUtils;
import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.service.EngineService;

/**
 * Created by gcampagn on 7/22/16.
 */
public class ContentAPI extends JavascriptAPI {
    private final StreamAPI streams;
    private final EngineService ctx;

    public ContentAPI(EngineService ctx, ControlChannel control, StreamAPI streams) {
        super("Content", control);

        this.ctx = ctx;
        this.streams = streams;

        registerAsync("getStream", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return getStream((String)args[0]);
            }
        });
    }

    private JSONObject getStream(final String url) throws IOException, JSONException {
        final StreamAPI.Stream stream = streams.createStream();
        final Pair<InputStream, String> pair = ContentUtils.readUrl(ctx, url);

        try {
            streams.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        stream.forwardSync(pair.first);
                        pair.first.close();
                    } catch (IOException e) {
                        stream.error(e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            pair.first.close();
        }

        JSONObject json = new JSONObject();
        json.put("token", stream.getToken());
        json.put("contentType", pair.second);
        return json;
    }
}
