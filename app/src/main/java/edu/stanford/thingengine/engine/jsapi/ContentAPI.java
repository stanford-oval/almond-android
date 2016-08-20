package edu.stanford.thingengine.engine.jsapi;

import java.io.IOException;
import java.io.InputStream;

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
                    try (InputStream is = ContentUtils.readUrl(ctx, url).first) {
                        stream.forwardSync(is);
                    }
                } catch(IOException e) {
                    stream.error(e);
                }
            }
        });

        return stream.getToken();
    }
}
