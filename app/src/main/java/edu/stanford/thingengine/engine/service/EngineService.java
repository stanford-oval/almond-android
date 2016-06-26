package edu.stanford.thingengine.engine.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

import edu.stanford.thingengine.engine.jsapi.AssistantAPI;

public class EngineService extends Service {
    public static final String LOG_TAG = "thingengine.Service";

    private ControlChannel control;
    private AssistantAPI assistant;
    private volatile boolean frontendReady;
    private EngineThread engineThread;
    
    public EngineService() {
    }

    public boolean isFrontendReady() {
        return frontendReady;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (engineThread != null)
            return START_STICKY;

        Log.i(LOG_TAG, "Starting service");
        try {
            startThread();

            Log.i(LOG_TAG, "Started service");
            return START_STICKY;
        } catch(Exception e) {
            Log.e(LOG_TAG, "Exception while creating the nodejs thread", e);
            throw new RuntimeException(e);
        }
    }

    private void startThread() {
        control = null;
        frontendReady = false;

        engineThread = new EngineThread(this);
        engineThread.start();
    }

    // these methods are called from the EngineThread
    void engineBroken() {
        stopSelf();
    }
    void controlReady(AssistantAPI assistant, ControlChannel control) {
        synchronized (this) {
            this.assistant = assistant;
            this.control = control;
            notifyAll();
        }
    }
    void frontendReady() {
        // FIXME remove
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "Destroying service");
        try {
            if (engineThread != null) {
                ControlChannel control;
                synchronized (this) {
                    control = this.control;
                }
                if (control != null)
                    control.sendStop();

                try {
                    // give the thread 10 seconds to die
                    engineThread.join(10000);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "InterruptedException while destroying the nodejs thread", e);
                }
                engineThread = null;
                if (control != null)
                    control.close();
            }
        } catch(IOException e) {
            Log.e(LOG_TAG, "IOException while destroying the nodejs thread", e);
        }
        Log.i(LOG_TAG, "Destroyed service");
    }

    @Override
    public IBinder onBind(Intent intent) {
        onStartCommand(null, 0, 0);
        try {
            synchronized (this) {
                while (control == null)
                    wait();
            }
            return new ControlBinder(this, assistant, control);
        } catch(InterruptedException e) {
            return null;
        }
    }
}
