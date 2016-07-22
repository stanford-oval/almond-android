package edu.stanford.thingengine.engine.jsapi;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

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

        registerSync("getStream", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return getStream((String)args[0]);
            }
        });
    }

    private int getStream(final String url) {
        final StreamAPI.Stream stream = streams.createStream();

        streams.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri parsed = Uri.parse(url);
                    String scheme = parsed.getScheme();
                    if (scheme.equals(ContentResolver.SCHEME_CONTENT) ||
                            scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE) ||
                            scheme.equals(ContentResolver.SCHEME_FILE)) {
                        ContentResolver resolver = ctx.getContentResolver();


                        stream.forwardSync(resolver.openInputStream(parsed));
                    } else {
                        URL javaUrl = new URL(url);
                        URLConnection connection = javaUrl.openConnection();

                        stream.forwardSync(connection.getInputStream());
                    }
                } catch(IOException e) {
                    stream.error(e);
                }
            }
        });

        return stream.getToken();
    }
}
