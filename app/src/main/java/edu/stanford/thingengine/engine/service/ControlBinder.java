package edu.stanford.thingengine.engine.service;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.thingengine.engine.CloudAuthInfo;
import edu.stanford.thingengine.engine.IThingEngine;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 8/16/15.
 */
public class ControlBinder extends IThingEngine.Stub {
    private final EngineService service;
    private final ControlChannel channel;

    public static class DeviceInfo {
        public enum Tier { PHONE, SERVER, CLOUD, GLOBAL };
        public final String uniqueId;
        public final String kind;
        public final String name;
        public final String description;
        public final Tier ownerTier;
        public final boolean isTransient;
        public final boolean isOnlineAccount;
        public final boolean isDataSource;
        public final boolean isThingEngine;

        private DeviceInfo(JSONObject json) throws JSONException {
            uniqueId = json.getString("uniqueId");
            kind = json.getString("kind");
            name = json.getString("name");
            description = json.getString("description");
            ownerTier = Tier.valueOf(json.getString("ownerTier").toUpperCase());
            isTransient = json.getBoolean("isTransient");
            isOnlineAccount = json.getBoolean("isOnlineAccount");
            isDataSource = json.getBoolean("isDataSource");
            isThingEngine = json.getBoolean("isThingEngine");
        }
    }

    public ControlBinder(EngineService service, ControlChannel channel) {
        this.service = service;
        this.channel = channel;
    }

    public AssistantDispatcher getAssistant() {
        return service.getAssistant();
    }

    public void setInteractionCallback(InteractionCallback callback) {
        service.setInteractionCallback(callback);
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

    public boolean deleteDevice(String uniqueId) throws Exception {
        return channel.sendDeleteDevice(uniqueId);
    }

    public List<DeviceInfo> getDeviceInfos() throws Exception {
        JSONArray jsonDeviceInfos = channel.sendGetDeviceInfos();

        List<DeviceInfo> deviceInfos = new ArrayList<>();
        for (int i = 0; i < jsonDeviceInfos.length(); i++)
            deviceInfos.add(new DeviceInfo(jsonDeviceInfos.getJSONObject(i)));

        return deviceInfos;
    }
}
