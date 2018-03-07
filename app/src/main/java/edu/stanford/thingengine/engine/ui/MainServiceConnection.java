// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.common.api.Status;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import edu.stanford.thingengine.engine.service.ControlBinder;

/**
 * Created by gcampagn on 6/27/16.
 */
public class MainServiceConnection extends EngineServiceConnection implements InteractionCallback {
    private static final int MSG_RESOLVE_RESULT = 1;
    private static final int MSG_START_ACTIVITY = 2;
    private static final int MSG_REQUEST_PERMISSION = 3;

    private static class InteractionState implements Future<Boolean> {
        private boolean interacting = true;
        private boolean interacted = false;

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public synchronized boolean isCancelled() {
            return false;
        }

        @Override
        public synchronized boolean isDone() {
            return interacted;
        }

        @Override
        public synchronized Boolean get() throws InterruptedException {
            while (interacting)
                wait();
            return Boolean.valueOf(interacted);
        }

        @Override
        public Boolean get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, TimeoutException {
            while (interacting)
                unit.timedWait(this, timeout);
            return Boolean.valueOf(interacted);
        }

        public synchronized void complete(boolean result) {
            interacted = result;
            interacting = false;
            notifyAll();
        }
    }

    private static class InternalHandler extends Handler {
        private final WeakReference<MainServiceConnection> ref;

        public InternalHandler(WeakReference<MainServiceConnection> ref) {
            super(Looper.getMainLooper());
            this.ref = ref;
        }

        @Override
        public void handleMessage(Message msg) {
            MainServiceConnection parent = ref.get();
            if (parent == null)
                return;

            switch (msg.what) {
                case MSG_RESOLVE_RESULT:
                    parent.doResolveResult((Status)msg.obj, msg.arg1);
                    break;

                case MSG_START_ACTIVITY:
                    parent.doStartActivity((Intent)msg.obj, msg.arg1);
                    break;

                case MSG_REQUEST_PERMISSION:
                    parent.doRequestPermission((String)msg.obj, msg.arg1);
            }
        }
    }

    private final List<Runnable> callbacks = new ArrayList<>();
    private final Map<Integer, InteractionState> interacting = new ConcurrentHashMap<>();
    private final InternalHandler handler;

    public MainServiceConnection() {
        handler = new InternalHandler(new WeakReference<MainServiceConnection>(this));
    }

    private void doResolveResult(Status status, int requestCode) {
        InteractionState state = interacting.get(requestCode);
        if (state == null)
            return;

        Activity currentParent = parent;
        if (currentParent == null) {
            state.complete(false);
            return;
        }

        try {
            status.startResolutionForResult(currentParent, requestCode);
        } catch (IntentSender.SendIntentException e) {
            state.complete(false);
        }
    }

    private void doStartActivity(Intent intent, int requestCode) {
        InteractionState state = interacting.get(requestCode);
        if (state == null)
            return;

        Activity currentParent = parent;
        if (currentParent == null) {
            state.complete(false);
            return;
        }

        currentParent.startActivityForResult(intent, requestCode);
    }

    private void doRequestPermission(String permission, int requestCode) {
        InteractionState state = interacting.get(requestCode);
        if (state == null)
            return;

        Activity currentParent = parent;
        if (currentParent == null) {
            state.complete(false);
            return;
        }

        ActivityCompat.requestPermissions(currentParent, new String[]{permission}, requestCode);
    }

    @Override
    public boolean resolveResult(Status status, int requestCode) throws InterruptedException {
        final InteractionState state = new InteractionState();
        interacting.put(requestCode, state);
        handler.obtainMessage(MSG_RESOLVE_RESULT, requestCode, 0, status).sendToTarget();

        return state.get();
    }

    @Override
    public boolean startActivity(final Intent intent, final int requestCode) throws InterruptedException {
        final InteractionState state = new InteractionState();
        interacting.put(requestCode, state);
        handler.obtainMessage(MSG_START_ACTIVITY, requestCode, 0, intent).sendToTarget();

        return state.get();
    }

    @Override
    public boolean requestPermission(String permission, int requestCode) throws InterruptedException {
        final InteractionState state = new InteractionState();
        interacting.put(requestCode, state);
        handler.obtainMessage(MSG_REQUEST_PERMISSION, requestCode, 0, permission).sendToTarget();

        return state.get();
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

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        InteractionState state = interacting.remove(requestCode);
        if (state == null)
            return;
        state.complete(resultCode == Activity.RESULT_OK);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        InteractionState state = interacting.remove(requestCode);
        if (state == null)
            return;
        boolean granted = true;
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        state.complete(granted);
    }

    public void addEngineReadyCallback(Runnable callback) {
        callbacks.add(callback);
    }

    public void removeEngineReadyCallback(Runnable callback) {
        callbacks.remove(callback);
    }
}
