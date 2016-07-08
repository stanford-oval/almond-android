package edu.stanford.thingengine.engine.jsapi;

import android.content.Context;
import android.media.AudioManager;

import edu.stanford.thingengine.engine.service.ControlChannel;

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

        registerSync("adjustMediaVolume", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                adjustMediaVolume(((Number)args[0]).intValue(), (Boolean)args[1]);
                return null;
            }
        });

        registerSync("setMediaVolume", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                setMediaVolume(((Number)args[0]).doubleValue(), (Boolean)args[1]);
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

    private void adjustMediaVolume(int direction, boolean playSound) {
        AudioManager mgr = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        int directionEnum;
        if (direction > 0)
            directionEnum = AudioManager.ADJUST_RAISE;
        else if (direction < 0)
            directionEnum = AudioManager.ADJUST_LOWER;
        else
            directionEnum = AudioManager.ADJUST_SAME;

        mgr.adjustStreamVolume(AudioManager.STREAM_MUSIC, directionEnum, playSound ? AudioManager.FLAG_PLAY_SOUND : 0);
    }

    private void setMediaVolume(double volume, boolean playSound) {
        AudioManager mgr = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        int maxVolume = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int hwVolume = (int)Math.round(volume/100.0 * maxVolume);
        mgr.setStreamVolume(AudioManager.STREAM_MUSIC, hwVolume, playSound ? AudioManager.FLAG_PLAY_SOUND : 0);
    }
}
