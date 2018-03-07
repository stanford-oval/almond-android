// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.nodejs;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.util.Log;

import java.util.concurrent.ExecutorService;

/**
 * Created by gcampagn on 4/30/17.
 */

public class NodeJSLauncher {
    public static void launch(final Context ctx) {
        launchNodeNative(ctx.getAssets(), ctx.getClassLoader());
    }

    private static void asyncCallback(ExecutorService service, final long promiseId, final JavaCallback callback, final Object[] args) throws Exception {
        service.execute(new Runnable() {
            @Override
            public void run() {
                Object res = null;
                Exception err = null;
                try {
                    res = callback.invoke(args);
                } catch(Exception e) {
                    err = e;
                }
                if (promiseId != 0)
                    completePromise(promiseId, res, err);
            }
        });
    }

    private static native Object launchNodeNative(AssetManager assetManager, ClassLoader loader);
    private static native void completePromise(long promiseId, Object result, Exception error);

    public static native void invokeAsync(String fn, Object... args);
    public static native Object invokeSync(String fn, Object... args) throws Exception;
    public static native void registerJavaCall(String fn, JavaCallback callback);

    static {
        System.loadLibrary("node-launcher");
    }
}
