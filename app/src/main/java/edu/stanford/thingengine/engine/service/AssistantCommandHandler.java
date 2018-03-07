// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.service;

import org.json.JSONObject;

/**
 * Created by gcampagn on 7/10/16.
 */
public interface AssistantCommandHandler {
    void ready();

    void handleCommand(String command);

    void handleParsedCommand(JSONObject json);

    void handleThingTalk(String code);

    void presentExample(String utterance, String targetCode);
}
