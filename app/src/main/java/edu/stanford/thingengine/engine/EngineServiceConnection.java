package edu.stanford.thingengine.engine;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.google.android.gms.common.api.Status;
/**
 * Created by gcampagn on 8/16/15.
 */

public class EngineServiceConnection implements ServiceConnection, InteractionCallback {
    private volatile WebUIActivity parent;
    private volatile ControlBinder binder;

    private boolean interacting = false;
    private boolean interacted = false;

    @Override
    public void frontendReady() {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebUIActivity currentParent = parent;
                if (currentParent == null)
                    return;
                currentParent.onFrontendReady();
            }
        });
    }

    @Override
    public boolean resolveResult(final Status status) throws InterruptedException {
        Activity currentParent = parent;
        if (currentParent == null)
            return false;

        synchronized (this) {
            while (interacting)
                wait();
            interacting = true;
            currentParent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Activity currentParent = parent;
                    if (currentParent == null) {
                        synchronized (EngineServiceConnection.this) {
                            interacting = false;
                            interacted = false;
                            EngineServiceConnection.this.notifyAll();
                        }
                        return;
                    }

                    try {
                        status.startResolutionForResult(currentParent, 1);
                    } catch(IntentSender.SendIntentException e) {
                        synchronized (EngineServiceConnection.this) {
                            interacting = false;
                            interacted = false;
                            EngineServiceConnection.this.notifyAll();
                        }
                    }
                }
            });

            while (interacting)
                wait();
            return interacted;
        }
    }

    public ControlBinder getControl() {
        return binder;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (ControlBinder)service;
        binder.setInteractionCallback(this);
        if (binder.isFrontendReady())
            this.frontendReady();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
    }

    public void start(WebUIActivity ctx) {
        parent = ctx;
        Intent intent = new Intent(ctx, EngineService.class);
        ctx.bindService(intent, this, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
    }

    public void stop(Activity ctx) {
        parent = null;

        ControlBinder oldBinder = binder;
        if (oldBinder != null)
            binder.setInteractionCallback(null);
        ctx.unbindService(this);
    }

    public synchronized void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (!interacting)
            return;
        if (requestCode != 1)
            return;
        interacting = false;
        interacted = resultCode == Activity.RESULT_OK;
        notifyAll();
    }
}
