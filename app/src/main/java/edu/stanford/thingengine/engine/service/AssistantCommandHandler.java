package edu.stanford.thingengine.engine.service;

/**
 * Created by gcampagn on 7/10/16.
 */
public interface AssistantCommandHandler {
    void ready();

    void handleCommand(String command);

    void handleParsedCommand(String json);
}
