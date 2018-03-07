// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.ui;

import android.content.Intent;

import com.google.android.gms.common.api.Status;

/**
 * Created by gcampagn on 5/7/16.
 */
public interface InteractionCallback {
    int ENABLE_GPS = 101;
    int ENABLE_BLUETOOTH = 102;
    int REQUEST_SMS = 201;
    int REQUEST_CONTACTS = 202;
    int REQUEST_CALL = 203;
    int REQUEST_GPS = 204;

    boolean requestPermission(String permission, int requestCode) throws InterruptedException;
    boolean resolveResult(Status status, int requestCode) throws InterruptedException;
    boolean startActivity(Intent intent, int requestCode) throws InterruptedException;
}
