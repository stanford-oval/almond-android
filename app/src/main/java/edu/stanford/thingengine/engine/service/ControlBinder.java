package edu.stanford.thingengine.engine.service;

import org.json.JSONArray;
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

    public boolean upgradeDevice(String kind) throws Exception {
        return channel.sendUpgradeDevice(kind);
    }

    public List<DeviceInfo> getDeviceInfos() throws Exception {
        JSONArray jsonDeviceInfos = channel.sendGetDeviceInfos();

        List<DeviceInfo> deviceInfos = new ArrayList<>();
        for (int i = 0; i < jsonDeviceInfos.length(); i++)
            deviceInfos.add(new DeviceInfo(jsonDeviceInfos.getJSONObject(i)));

        return deviceInfos;
    }

    public DeviceInfo getDeviceInfo(String uniqueId) throws Exception {
        return new DeviceInfo(channel.sendGetDeviceInfo(uniqueId));
    }

    public int checkDeviceAvailable(String uniqueId) throws Exception {
        return channel.sendCheckDeviceAvailable(uniqueId);
    }

    public List<AppInfo> getAppInfos() throws Exception {
        JSONArray jsonAppInfos = channel.sendGetAppInfos();

        List<AppInfo> appInfos = new ArrayList<>();
        for (int i = 0; i < jsonAppInfos.length(); i++)
            appInfos.add(new AppInfo(jsonAppInfos.getJSONObject(i)));

        return appInfos;
    }

    public void deleteApp(String uniqueId) throws Exception {
        channel.sendDeleteApp(uniqueId);
    }
}
