// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.service;

/**
 * Created by gcampagn on 8/13/16.
 */
public interface AssistantLifecycleCallbacks {
    void onBeforeCommand();
    void onAfterCommand();
}
