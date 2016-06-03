package edu.stanford.thingengine.engine;

import com.google.android.gms.common.api.Status;

/**
 * Created by gcampagn on 5/7/16.
 */
public interface InteractionCallback {
    boolean resolveResult(Status status) throws InterruptedException;
    void frontendReady();
}
