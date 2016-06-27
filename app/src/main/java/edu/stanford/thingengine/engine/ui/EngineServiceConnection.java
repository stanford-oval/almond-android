package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.EngineService;

/**
 * Created by gcampagn on 8/16/15.
 */

public class EngineServiceConnection implements ServiceConnection {
    protected volatile Activity parent;
    protected volatile ControlBinder binder;

    public ControlBinder getControl() {
        return binder;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (ControlBinder)service;
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
        ctx.unbindService(this);
    }
}
