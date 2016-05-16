package edu.stanford.thingengine.engine.jsapi;

import android.content.Context;
import android.media.AudioManager;

import edu.stanford.thingengine.engine.ControlChannel;

/**
 * Created by gcampagn on 5/16/16.
 */
public class AudioManagerAPI extends JavascriptAPI {
    private final Context ctx;

    public AudioManagerAPI(Context ctx, ControlChannel control) {
        super("AudioManager", control);

        this.ctx = ctx;

        registerSync("setRingerMode", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                setRingerMode((String)args[0]);
                return null;
            }
        });
    }

    private void setRingerMode(String modeString) {
        int mode;
        switch(modeString) {
            case "normal":
                mode = AudioManager.RINGER_MODE_NORMAL;
                break;
            case "vibrate":
                mode = AudioManager.RINGER_MODE_VIBRATE;
                break;
            case "silent":
                mode = AudioManager.RINGER_MODE_SILENT;
                break;
            default:
                throw new IllegalArgumentException("Invalid ringer mode");
        }

        AudioManager mgr = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        mgr.setRingerMode(mode);
    }
}
