package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.os.IBinder;

import com.google.android.gms.common.api.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.thingengine.engine.service.ControlBinder;

/**
 * Created by gcampagn on 6/27/16.
 */
public class MainServiceConnection extends EngineServiceConnection implements InteractionCallback {
    private final List<Runnable> callbacks = new ArrayList<>();

    private static class InteractionState {
        public boolean interacting = false;
        public boolean interacted = false;
    }

    private final Map<Integer, InteractionState> interacting = new HashMap<>();

    @Override
    public boolean resolveResult(final Status status, final int requestCode) throws InterruptedException {
        Activity currentParent = parent;
        if (currentParent == null)
            return false;

        synchronized (this) {
            final InteractionState state = new InteractionState();
            interacting.put(requestCode, state);
            state.interacting = true;
            currentParent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Activity currentParent = parent;
                    if (currentParent == null) {
                        synchronized (MainServiceConnection.this) {
                            state.interacting = false;
                            state.interacted = false;
                            MainServiceConnection.this.notifyAll();
                        }
                        return;
                    }

                    try {
                        status.startResolutionForResult(currentParent, 1);
                    } catch(IntentSender.SendIntentException e) {
                        synchronized (MainServiceConnection.this) {
                            state.interacting = false;
                            state.interacted = false;
                            MainServiceConnection.this.notifyAll();
                        }
                    }
                }
            });

            while (state.interacting)
                wait();
            interacting.remove(requestCode);
            return state.interacted;
        }
    }

    @Override
    public boolean startActivity(final Intent intent, final int requestCode) throws InterruptedException {
        Activity currentParent = parent;
        if (currentParent == null)
            return false;

        synchronized (this) {
            final InteractionState state = new InteractionState();
            interacting.put(requestCode, state);
            state.interacting = true;
            currentParent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Activity currentParent = parent;
                    if (currentParent == null) {
                        synchronized (MainServiceConnection.this) {
                            state.interacting = false;
                            state.interacted = false;
                            MainServiceConnection.this.notifyAll();
                        }
                        return;
                    }

                    currentParent.startActivityForResult(intent, requestCode);
                }
            });

            while (state.interacting)
                wait();
            interacting.remove(requestCode);
            return state.interacted;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);
        binder.setInteractionCallback(this);

        for (Runnable r : callbacks)
            r.run();
    }

    @Override
    public void stop(Activity ctx) {
        ControlBinder oldBinder = binder;
        if (oldBinder != null)
            binder.setInteractionCallback(null);

        super.stop(ctx);
    }

    public synchronized void onActivityResult(int requestCode, int resultCode, Intent intent) {
        final InteractionState state = interacting.get(requestCode);
        if (state == null || !state.interacting)
            return;
        state.interacting = false;
        state.interacted = resultCode == Activity.RESULT_OK;
        notifyAll();
    }

    public void addEngineReadyCallback(Runnable callback) {
        callbacks.add(callback);
    }

    public void removeEngineReadyCallback(Runnable callback) {
        callbacks.remove(callback);
    }
}
