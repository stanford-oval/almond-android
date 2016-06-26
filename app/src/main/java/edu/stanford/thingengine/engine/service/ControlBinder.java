package edu.stanford.thingengine.engine.service;

import edu.stanford.thingengine.engine.CloudAuthInfo;
import edu.stanford.thingengine.engine.IThingEngine;
import edu.stanford.thingengine.engine.jsapi.AssistantAPI;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 8/16/15.
 */
public class ControlBinder extends IThingEngine.Stub {
    private final EngineService service;
    private final AssistantAPI assistant;
    private final ControlChannel channel;

    public ControlBinder(EngineService service, AssistantAPI assistant, ControlChannel channel) {
        this.service = service;
        this.assistant = assistant;
        this.channel = channel;
    }

    public AssistantAPI getAssistant() {
        return assistant;
    }

    public boolean setCloudId(CloudAuthInfo authInfo) {
        return channel.sendSetCloudId(authInfo);
    }

    public boolean setServerAddress(String host, int port, String authToken) {
        return channel.sendSetServerAddress(host, port, authToken);
    }

    public void setInteractionCallback(InteractionCallback callback) {
        channel.setInteractionCallback(callback);
    }
}
