package edu.stanford.thingengine.engine.service;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public void setInteractionCallback(InteractionCallback callback) {
        channel.setInteractionCallback(callback);
    }

    public boolean setCloudId(CloudAuthInfo authInfo) {
        return channel.sendSetCloudId(authInfo);
    }

    public boolean setServerAddress(String host, int port, String authToken) {
        return channel.sendSetServerAddress(host, port, authToken);
    }

    public boolean handleOAuth2Callback(String kind, JSONObject req) throws Exception {
        return channel.sendHandleOAuth2Callback(kind, req);
    }

    public JSONArray startOAuth2(String kind) throws Exception {
        return channel.sendStartOAuth2(kind);
    }

    public boolean createDevice(JSONObject object) throws Exception {
        return channel.sendCreateDevice(object);
    }
}
