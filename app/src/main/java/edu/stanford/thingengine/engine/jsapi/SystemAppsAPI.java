// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.jsapi;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.KeyEvent;

/**
 * Created by gcampagn on 7/7/16.
 */
public class SystemAppsAPI extends JavascriptAPI {
    private final Context ctx;

    public SystemAppsAPI(Context ctx) {
        super("SystemApps");

        this.ctx = ctx;

        registerSync("startMusic", new Runnable() {
            @Override
            public void run() {
                startMusic();
            }
        });
    }

    private void startMusic() {
        Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);

        // cheat a bit and pretend play was pressed
        AudioManager mgr = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        KeyEvent playDown = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
        KeyEvent playUp = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY);
        mgr.dispatchMediaKeyEvent(playDown);
        mgr.dispatchMediaKeyEvent(playUp);
    }
}
