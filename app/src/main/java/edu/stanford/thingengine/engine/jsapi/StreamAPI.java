package edu.stanford.thingengine.engine.jsapi;

import android.util.Base64;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.thingengine.engine.service.ControlChannel;

/**
 * Created by gcampagn on 7/21/16.
 */
public class StreamAPI extends JavascriptAPI {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Executor executor = Executors.newFixedThreadPool(6);

    public StreamAPI(ControlChannel control) {
        super("StreamAPI", control);
    }

    public class Stream {
        private final int token;

        private Stream() {
            token = counter.addAndGet(1);
        }

        public int getToken() {
            return token;
        }

        public void push(byte[] data, int offset, int length) {
            JSONArray array = new JSONArray();
            array.put(token);
            array.put(Base64.encode(data, offset, length, Base64.DEFAULT));
            invokeAsync("onstreamdata", array);
        }

        public void push(byte[] data) {
            push(data, 0, data.length);
        }

        public void end() {
            invokeAsync("onstreamend", token);
        }

        public void error(Exception e) {
            JSONArray array = new JSONArray();
            array.put(token);
            array.put(e.getMessage());
            invokeAsync("onstreamerror", array);
        }

        public void forwardSync(InputStream is) {
            try {
                byte[] buffer = new byte[4096];
                for (int read = is.read(buffer); read > 0; read = is.read(buffer))
                    push(buffer, 0, read);
                end();
            } catch (IOException e) {
                error(e);
            }
        }

        public void forward(final InputStream is) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    forwardSync(is);
                }
            });
        }
    }

    public Stream createStream() {
        return new Stream();
    }

    public Executor getThreadPool() {
        return executor;
    }
}
