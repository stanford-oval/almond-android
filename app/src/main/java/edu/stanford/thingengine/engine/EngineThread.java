package edu.stanford.thingengine.engine;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import edu.stanford.thingengine.engine.jsapi.AudioManagerAPI;
import edu.stanford.thingengine.engine.jsapi.GpsAPI;
import edu.stanford.thingengine.engine.jsapi.JSSharedPreferences;
import edu.stanford.thingengine.engine.jsapi.NotifyAPI;
import edu.stanford.thingengine.engine.jsapi.UnzipAPI;
import io.jxcore.node.jxcore;

/**
 * Created by gcampagn on 8/8/15.
 */
public class EngineThread extends Thread {
    private final Context context;
    private final Lock initLock;
    private final Condition initCondition;
    private final HandlerThread worker;
    private boolean controlReady;
    private boolean isLocked;
    private boolean broken;
    private volatile ControlChannel control;
    private Handler workerHandler;

    public EngineThread(Context context, Lock initLock, Condition initCondition) {
        this.context = context;
        this.initLock = initLock;
        this.initCondition = initCondition;
        controlReady = false;
        broken = false;
        isLocked = false;
        worker = new HandlerThread("EngineWorker");
    }

    public boolean isControlReady() {
        return controlReady;
    }

    public boolean isBroken() {
        return broken;
    }

    public ControlChannel getControlChannel() {
        return control;
    }

    @Override
    public void run() {
        worker.start();
        workerHandler = new Handler(worker.getLooper());

        jxcore.Initialize(context.getApplicationContext());

        try {
            initLock.lock();
            isLocked = true;
            jxcore.RegisterMethod("controlReady", new jxcore.JXcoreCallback() {
                @Override
                public void Receiver(ArrayList<Object> params, String callbackId) {
                    controlReady = true;
                    try {
                        control = new ControlChannel(context);
                    } catch(IOException e) {
                        Log.e(EngineService.LOG_TAG, "Failed to acquire control channel!", e);
                    }
                    initCondition.signalAll();
                    initLock.unlock();
                    isLocked = false;

                    new NotifyAPI(context, control);
                    new UnzipAPI(control);
                    new GpsAPI(workerHandler, context, control);
                    new AudioManagerAPI(context, control);
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
                broken = true;
                initCondition.signalAll();
            }
        } finally {
            if (isLocked)
                initLock.unlock();
            worker.quit();
        }
        jxcore.StopEngine();
    }
}
