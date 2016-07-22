package edu.stanford.thingengine.engine.service;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import edu.stanford.thingengine.engine.jsapi.AssistantAPI;
import edu.stanford.thingengine.engine.jsapi.AudioManagerAPI;
import edu.stanford.thingengine.engine.jsapi.AudioRouterAPI;
import edu.stanford.thingengine.engine.jsapi.BluetoothAPI;
import edu.stanford.thingengine.engine.jsapi.ContentAPI;
import edu.stanford.thingengine.engine.jsapi.GpsAPI;
import edu.stanford.thingengine.engine.jsapi.ImageAPI;
import edu.stanford.thingengine.engine.jsapi.JSSharedPreferences;
import edu.stanford.thingengine.engine.jsapi.NotifyAPI;
import edu.stanford.thingengine.engine.jsapi.SmsAPI;
import edu.stanford.thingengine.engine.jsapi.StreamAPI;
import edu.stanford.thingengine.engine.jsapi.SystemAppsAPI;
import edu.stanford.thingengine.engine.jsapi.UnzipAPI;
import io.jxcore.node.jxcore;

/**
 * Created by gcampagn on 8/8/15.
 */
public class EngineThread extends Thread {
    private final EngineService context;
    private ControlChannel control;
    private boolean controlReady = false;

    public EngineThread(EngineService context) {
        this.context = context;
    }

    @Override
    public void run() {
        final HandlerThread worker = new HandlerThread("EngineWorker");
        worker.start();
        final Handler workerHandler = new Handler(worker.getLooper());

        jxcore.Initialize(context.getApplicationContext());

        try {
            jxcore.RegisterMethod("controlReady", new jxcore.JXcoreCallback() {
                @Override
                public void Receiver(ArrayList<Object> params, String callbackId) {
                    controlReady = true;
                    try {
                        control = new ControlChannel(context);
                    } catch(IOException e) {
                        Log.e(EngineService.LOG_TAG, "Failed to acquire control channel!", e);
                        return;
                    }

                    new NotifyAPI(context, control);
                    new UnzipAPI(control);
                    new GpsAPI(workerHandler, context, control);
                    new AudioManagerAPI(context, control);
                    new SmsAPI(workerHandler, context, control);
                    new BluetoothAPI(workerHandler, context, control);
                    new AudioRouterAPI(workerHandler, context, control);
                    new SystemAppsAPI(context, control);
                    StreamAPI stream = new StreamAPI(control);
                    new ImageAPI(control, stream);
                    new ContentAPI(context, control, stream);

                    context.controlReady(new AssistantAPI(context, control), control);
                }
            });

            // shared preferences need to be initialized early, but luckily they don't have
            // async methods so they don't need the control channel
            new JSSharedPreferences(context, null);
            jxcore.CallJSMethod("runEngine", new Object[]{});
            jxcore.Loop();

            if (!controlReady) {
                Log.e(EngineService.LOG_TAG, "Engine failed to signal control ready!");
                controlReady = true;
                context.engineBroken();
            }
        } finally {
            worker.quit();
        }
        jxcore.StopEngine();
    }
}
