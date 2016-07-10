package edu.stanford.thingengine.engine.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

import edu.stanford.thingengine.engine.ui.InteractionCallback;

public class EngineService extends Service {
    public static final String LOG_TAG = "thingengine.Service";

    private AssistantDispatcher assistant;
    private ControlChannel control;
    private volatile InteractionCallback callback;
    private EngineThread engineThread;
    
    public EngineService() {}

    public AssistantDispatcher getAssistant() {
        return assistant;
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

        engineThread = new EngineThread(this);
        engineThread.start();
    }

    // these methods are called from the EngineThread
    void engineBroken() {
        stopSelf();
    }
    void controlReady(AssistantCommandHandler cmdHandler, ControlChannel control) {
        synchronized (this) {
            assistant = new AssistantDispatcher(this, cmdHandler);
            this.control = control;
            notifyAll();
        }
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
            return new ControlBinder(this, control);
        } catch(InterruptedException e) {
            return null;
        }
    }
}
