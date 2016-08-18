package edu.stanford.thingengine.engine.ui;

import android.content.Intent;

import com.google.android.gms.common.api.Status;

/**
 * Created by gcampagn on 5/7/16.
 */
public interface InteractionCallback {
    int ENABLE_GPS = 11;
    int ENABLE_BLUETOOTH = 12;
    int REQUEST_SMS = 21;
    int REQUEST_CONTACTS = 22;
    int REQUEST_CALL = 23;
    int REQUEST_GPS = 24;

    boolean requestPermission(String permission, int requestCode) throws InterruptedException;
    boolean resolveResult(Status status, int requestCode) throws InterruptedException;
    boolean startActivity(Intent intent, int requestCode) throws InterruptedException;
}
