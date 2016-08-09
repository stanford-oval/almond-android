package edu.stanford.thingengine.engine.ui;

import android.content.Intent;

import com.google.android.gms.common.api.Status;

/**
 * Created by gcampagn on 5/7/16.
 */
public interface InteractionCallback {
    int ENABLE_GPS = 1;
    int ENABLE_BLUETOOTH = 2;
    int REQUEST_SMS = 3;
    int REQUEST_CONTACTS = 4;
    int REQUEST_CALL = 5;

    boolean requestPermission(String permission, int requestCode) throws InterruptedException;
    boolean resolveResult(Status status, int requestCode) throws InterruptedException;
    boolean startActivity(Intent intent, int requestCode) throws InterruptedException;
}
