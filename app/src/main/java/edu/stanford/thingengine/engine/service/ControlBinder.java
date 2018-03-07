package edu.stanford.thingengine.engine.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.thingengine.engine.CloudAuthInfo;
import edu.stanford.thingengine.engine.IThingEngine;
import edu.stanford.thingengine.engine.ui.InteractionCallback;
import edu.stanford.thingengine.nodejs.NodeJSLauncher;

/**
 * Created by gcampagn on 8/16/15.
 */
public class ControlBinder extends IThingEngine.Stub {
    private final EngineService service;

    public ControlBinder(EngineService service) {
        this.service = service;
    }

    public AssistantDispatcher getAssistant() {
        return service.getAssistant();
    }

    public void setInteractionCallback(InteractionCallback callback) {
        service.setInteractionCallback(callback);
    }

    public boolean setCloudId(CloudAuthInfo authInfo) throws Exception {
        return (boolean)NodeJSLauncher.invokeSync("Control_setCloudId", authInfo.getCloudId(), authInfo.getAuthToken());
    }

    public boolean setServerAddress(String host, int port, String authToken) throws Exception {
        return (boolean)NodeJSLauncher.invokeSync("Control_setServerAddress", host, port, authToken);
    }

    public boolean handleOAuth2Callback(String kind, JSONObject req) throws Exception {
        return (boolean)NodeJSLauncher.invokeSync("Control_handleOAuth2Callback", kind, req);
    }

    public JSONArray startOAuth2(String kind) throws Exception {
        return (JSONArray)NodeJSLauncher.invokeSync("Control_startOAuth2", kind);
    }

    public boolean createDevice(JSONObject object) throws Exception {
        return (boolean)NodeJSLauncher.invokeSync("Control_createDevice", object);
    }

    public boolean deleteDevice(String uniqueId) throws Exception {
        return (boolean)NodeJSLauncher.invokeSync("Control_deleteDevice", uniqueId);
    }

    public boolean upgradeDevice(String kind) throws Exception {
        return (boolean)NodeJSLauncher.invokeSync("Control_upgradeDevice", kind);
    }

    public List<DeviceInfo> getDeviceInfos() throws Exception {
        JSONArray jsonDeviceInfos = (JSONArray) NodeJSLauncher.invokeSync("Control_getDeviceInfos");

        List<DeviceInfo> deviceInfos = new ArrayList<>();
        for (int i = 0; i < jsonDeviceInfos.length(); i++)
            deviceInfos.add(new DeviceInfo(jsonDeviceInfos.getJSONObject(i)));

        return deviceInfos;
    }

    public DeviceInfo getDeviceInfo(String uniqueId) throws Exception {
        return new DeviceInfo((JSONObject) NodeJSLauncher.invokeSync("Control_getDeviceInfo", uniqueId));
    }

    public int checkDeviceAvailable(String uniqueId) throws Exception {
        return (int)(double)NodeJSLauncher.invokeSync("Control_checkDeviceAvailable", uniqueId);
    }

    public List<AppInfo> getAppInfos() throws Exception {
        JSONArray jsonAppInfos = (JSONArray) NodeJSLauncher.invokeSync("Control_getAppInfos");

        List<AppInfo> appInfos = new ArrayList<>();
        for (int i = 0; i < jsonAppInfos.length(); i++)
            appInfos.add(new AppInfo(jsonAppInfos.getJSONObject(i)));

        return appInfos;
    }

    public void deleteApp(String uniqueId) {
        NodeJSLauncher.invokeAsync("Control_deleteApp", uniqueId);
    }
    
    public List<PermissionInfo> getAllPermissions() throws Exception {
        JSONArray jsonAppInfos = (JSONArray) NodeJSLauncher.invokeSync("Control_getAllPermissions");

        List<PermissionInfo> appInfos = new ArrayList<>();
        for (int i = 0; i < jsonAppInfos.length(); i++)
            appInfos.add(new PermissionInfo(jsonAppInfos.getJSONObject(i)));

        return appInfos;
    }

    public void revokePermission(String uniqueId) {
        NodeJSLauncher.invokeAsync("Control_revokePermission", uniqueId);
    }
}
