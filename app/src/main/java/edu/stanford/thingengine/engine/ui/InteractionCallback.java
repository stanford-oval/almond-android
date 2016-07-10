package edu.stanford.thingengine.engine.ui;

import android.content.Intent;

import com.google.android.gms.common.api.Status;

/**
 * Created by gcampagn on 5/7/16.
 */
public interface InteractionCallback {
    int ENABLE_GPS = 1;
    int ENABLE_BLUETOOTH = 2;

    boolean resolveResult(Status status, int requestCode) throws InterruptedException;
    boolean startActivity(Intent intent, int requestCode) throws InterruptedException;
}
