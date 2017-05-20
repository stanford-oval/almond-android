package edu.stanford.thingengine.engine.jsapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by gcampagn on 7/21/16.
 */
public class ImageAPI extends JavascriptAPI {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Map<Integer, Image> images = new ConcurrentHashMap<>();
    private final StreamAPI streams;

    private class Image {
        private final int token;
        private Bitmap bitmap;

        public Image(String path) {
            token = counter.addAndGet(1);
            bitmap = BitmapFactory.decodeFile(path);
        }

        public Image(byte[] buffer) {
            token = counter.addAndGet(1);
            bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
        }

        public int getWidth() {
            return bitmap.getWidth();
        }

        public int getHeight() {
            return bitmap.getHeight();
        }

        public synchronized void resizeFit(int width, int height) {
            int currentWidth = bitmap.getWidth();
            int currentHeight = bitmap.getHeight();
            int targetWidth, targetHeight;
            if (currentWidth < currentHeight) {
                targetWidth = Math.round(height * currentWidth / currentHeight);
                targetHeight = height;
            } else {
                targetWidth = width;
                targetHeight = Math.round(width * currentHeight / currentWidth);
            }
            bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        }

        private void compress(String format, OutputStream stream) {
            Bitmap.CompressFormat cformat;
            switch (format) {
                case "jpeg":
                case "jpg":
                    cformat = Bitmap.CompressFormat.JPEG;
                    break;
                case "png":
                    cformat = Bitmap.CompressFormat.PNG;
                    break;
                case "webp":
                    cformat = Bitmap.CompressFormat.WEBP;
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported format " + format);
            }

            bitmap.compress(cformat, 90, stream);
        }

        public synchronized byte[] toBuffer(String format) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            compress(format, stream);
            return stream.toByteArray();
        }

        public synchronized int toStream(final String format) {
            final StreamAPI.Stream stream = streams.createStream();
            streams.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        PipedOutputStream output = new PipedOutputStream();
                        PipedInputStream input = new PipedInputStream();

                        output.connect(input);
                        stream.forward(input);
                        compress(format, output);
                    } catch(IOException e) {
                        stream.error(e);
                    }
                }
            });

            return stream.getToken();
        }
    }

    public ImageAPI(StreamAPI streams) {
        super("Image");

        this.streams = streams;

        registerAsync("createImage", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                if (args[0].equals("path"))
                    return createImageFromPath((String)args[1]);
                else
                    return createImageFromBuffer(Base64.decode((String)args[1], Base64.DEFAULT));
            }
        });

        registerAsync("resizeFit", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                resizeFit(((Number)args[0]).intValue(), ((Number)args[1]).intValue(), ((Number)args[2]).intValue());
                return null;
            }
        });

        registerSync("imageToBuffer", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return toBuffer(((Number)args[0]).intValue(), (String)args[1]);
            }
        });

        registerSync("imageToStream", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return toStream(((Number)args[0]).intValue(), (String)args[1]);
            }
        });

        registerSync("imageGetWidth", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return getWidth(((Number)args[0]).intValue());
            }
        });

        registerSync("imageGetHeight", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return getHeight(((Number)args[0]).intValue());
            }
        });

        registerSync("imageDispose", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                dispose(((Number)args[0]).intValue());
                return null;
            }
        });
    }

    private int createImageFromPath(String path) {
        Image newImage = new Image(path);
        images.put(newImage.token, newImage);
        return newImage.token;
    }

    private int createImageFromBuffer(byte[] buffer) {
        Image newImage = new Image(buffer);
        images.put(newImage.token, newImage);
        return newImage.token;
    }

    private void resizeFit(int token, int width, int height) {
        Image img = images.get(token);
        if (img == null)
            throw new RuntimeException("Invalid image token");
        img.resizeFit(width, height);
    }

    private byte[] toBuffer(int token, String format) {
        Image img = images.remove(token);
        if (img == null)
            throw new RuntimeException("Invalid image token");
        return img.toBuffer(format);
    }

    private int toStream(int token, String format) {
        Image img = images.remove(token);
        if (img == null)
            throw new RuntimeException("Invalid image token");
        return img.toStream(format);
    }

    private int getWidth(int token) {
        Image img = images.get(token);
        if (img == null)
            throw new RuntimeException("Invalid image token");
        return img.getWidth();
    }

    private int getHeight(int token) {
        Image img = images.get(token);
        if (img == null)
            throw new RuntimeException("Invalid image token");
        return img.getHeight();
    }

    private void dispose(int token) {
        images.remove(token);
    }
}
