package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;

import com.google.android.gms.common.api.Status;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.EngineService;

/**
 * Created by gcampagn on 8/16/15.
 */

public class EngineServiceConnection implements ServiceConnection, InteractionCallback {
    private volatile Activity parent;
    private volatile ControlBinder binder;
    private AssistantOutput assistantOutput;

    private static class InteractionState {
        public boolean interacting = false;
        public boolean interacted = false;
    }

    private final Map<Integer, InteractionState> interacting = new HashMap<>();

    @Override
    public void send(final String msg) {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assistantOutput != null)
                    assistantOutput.send(msg);
            }
        });
    }

    @Override
    public void sendPicture(final String url) {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assistantOutput != null)
                    assistantOutput.sendPicture(url);
            }
        });
    }

    @Override
    public void sendRDL(final JSONObject rdl) {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assistantOutput != null)
                    assistantOutput.sendRDL(rdl);
            }
        });
    }

    @Override
    public void sendChoice(final int idx, final String what, final String title, final String text) {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assistantOutput != null)
                    assistantOutput.sendChoice(idx, what, title, text);
            }
        });
    }

    @Override
    public void sendLink(final String title, final String url) {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assistantOutput != null)
                    assistantOutput.sendLink(title, url);
            }
        });
    }

    @Override
    public void sendButton(final String title, final String json) {
        Activity currentParent = parent;
        if (currentParent == null)
            return;
        currentParent.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (assistantOutput != null)
                    assistantOutput.sendButton(title, json);
            }
        });
    }

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
                        synchronized (EngineServiceConnection.this) {
                            state.interacting = false;
                            state.interacted = false;
                            EngineServiceConnection.this.notifyAll();
                        }
                        return;
                    }

                    try {
                        status.startResolutionForResult(currentParent, 1);
                    } catch(IntentSender.SendIntentException e) {
                        synchronized (EngineServiceConnection.this) {
                            state.interacting = false;
                            state.interacted = false;
                            EngineServiceConnection.this.notifyAll();
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
                        synchronized (EngineServiceConnection.this) {
                            state.interacting = false;
                            state.interacted = false;
                            EngineServiceConnection.this.notifyAll();
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

    public ControlBinder getControl() {
        return binder;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (ControlBinder)service;
        binder.setInteractionCallback(this);
        if (assistantOutput != null)
            assistantResume();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
    }

    public void start(Activity ctx) {
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
        final InteractionState state = interacting.get(requestCode);
        if (state == null || !state.interacting)
            return;
        state.interacting = false;
        state.interacted = resultCode == Activity.RESULT_OK;
        notifyAll();
    }

    public void setAssistantOutput(AssistantOutput output) {
        assistantOutput = output;
    }

    public void assistantResume() {
        final ControlBinder control = binder;
        if (control == null)
            return;

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                control.getAssistant().assistantResume();
            }
        });
    }

    public void handleAssistantCommand(final String command) {
        final ControlBinder control = binder;
        if (control == null)
            return;

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                control.getAssistant().handleCommand(command);
            }
        });
    }

    public void handleAssistantParsedCommand(final String json) {
        final ControlBinder control = binder;
        if (control == null)
            return;

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                control.getAssistant().handleParsedCommand(json);
            }
        });
    }

    public void handlePicture(final String url) {
        final ControlBinder control = binder;
        if (control == null)
            return;

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                control.getAssistant().handlePicture(url);
            }
        });
    }
}
