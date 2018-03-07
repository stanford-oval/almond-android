// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import edu.stanford.thingengine.engine.jsapi.AssistantAPI;
import edu.stanford.thingengine.engine.jsapi.AudioManagerAPI;
import edu.stanford.thingengine.engine.jsapi.AudioRouterAPI;
import edu.stanford.thingengine.engine.jsapi.BluetoothAPI;
import edu.stanford.thingengine.engine.jsapi.ContactAPI;
import edu.stanford.thingengine.engine.jsapi.ContentAPI;
import edu.stanford.thingengine.engine.jsapi.GpsAPI;
import edu.stanford.thingengine.engine.jsapi.ImageAPI;
import edu.stanford.thingengine.engine.jsapi.NotifyAPI;
import edu.stanford.thingengine.engine.jsapi.PlatformAPI;
import edu.stanford.thingengine.engine.jsapi.SharedPreferencesAPI;
import edu.stanford.thingengine.engine.jsapi.SmsAPI;
import edu.stanford.thingengine.engine.jsapi.StreamAPI;
import edu.stanford.thingengine.engine.jsapi.SystemAppsAPI;
import edu.stanford.thingengine.engine.jsapi.TelephoneAPI;
import edu.stanford.thingengine.engine.jsapi.UnzipAPI;
import edu.stanford.thingengine.engine.ui.InteractionCallback;
import edu.stanford.thingengine.nodejs.NodeJSLauncher;

public class EngineService extends Service {
    public static final String LOG_TAG = "thingengine.Service";

    private AssistantDispatcher assistant;
    private volatile InteractionCallback callback;
    private HandlerThread worker;
    private boolean started;

    public EngineService() {}

    public AssistantDispatcher getAssistant() {
        return assistant;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (started)
            return START_STICKY;

        Log.i(LOG_TAG, "Starting service");
        try {
            doStart();

            Log.i(LOG_TAG, "Started service");
            return START_STICKY;
        } catch(Exception e) {
            Log.e(LOG_TAG, "Exception while creating the nodejs thread", e);
            throw new RuntimeException(e);
        }
    }

    private void doStart() {
        started = true;
        worker = new HandlerThread("EngineWorker");
        worker.setDaemon(true);
        worker.start();
        final Handler workerHandler = new Handler(worker.getLooper());

        // initialize all the APIs that nodejs will need...
        new PlatformAPI(this);
        new SharedPreferencesAPI(this);
        new NotifyAPI(this);
        new UnzipAPI();
        new GpsAPI(workerHandler, this);
        new AudioManagerAPI(this);
        new SmsAPI(workerHandler, this);
        new BluetoothAPI(workerHandler, this);
        new AudioRouterAPI(workerHandler, this);
        new SystemAppsAPI(this);
        StreamAPI stream = new StreamAPI();
        new ImageAPI(stream);
        new ContentAPI(this, stream);
        new ContactAPI(this);
        new TelephoneAPI(this);
        assistant = new AssistantDispatcher(this, new AssistantAPI(this));

        // And start the nodejs code!
        NodeJSLauncher.launch(this);
    }

    public InteractionCallback getInteractionCallback() {
        return callback;
    }

    // private methods to be called only from the main thread
    void setInteractionCallback(InteractionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "Destroying service");
        NodeJSLauncher.invokeAsync("stop");
        Log.i(LOG_TAG, "Destroyed service");
    }

    @Override
    public IBinder onBind(Intent intent) {
        onStartCommand(null, 0, 0);
        return new ControlBinder(this);
    }
}
