package edu.stanford.thingengine.engine.jsapi;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

import edu.stanford.thingengine.nodejs.JavaCallback;

import static java.nio.charset.Charset.isSupported;

/**
 * Created by gcampagn on 5/22/17.
 */

public class PlatformAPI extends JavascriptAPI {
    private final Context ctx;

    public PlatformAPI(Context _ctx) {
        super("Platform");
        this.ctx = _ctx;

        this.register("getFilesDir", new JavaCallback() {
            @Override
            public Object invoke(Object... args) throws Exception {
                return ctx.getFilesDir().getAbsolutePath();
            }
        });

        this.register("getCacheDir", new JavaCallback() {
            @Override
            public Object invoke(Object... args) throws Exception {
                return ctx.getCacheDir().getAbsolutePath();
            }
        });

        this.register("getLocale", new JavaCallback() {
            @Override
            public Object invoke(Object... args) throws Exception {
                Locale locale = Locale.getDefault();
                String localeTag;
                if (isSupported(locale.getLanguage()))
                    localeTag = locale.toLanguageTag();
                else
                    localeTag = "en-US";
                return localeTag;
            }
        });

        this.register("getTimezone", new JavaCallback() {
            @Override
            public Object invoke(Object... args) throws Exception {
                return Calendar.getInstance().getTimeZone().getID();
            }
        });
    }
}
