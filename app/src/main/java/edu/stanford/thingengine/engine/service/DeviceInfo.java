package edu.stanford.thingengine.engine.service;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by gcampagn on 7/13/16.
 */
public class DeviceInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tier {PHONE, SERVER, CLOUD, GLOBAL}

    public final String uniqueId;
    public final String kind;
    public final String name;
    public final String description;
    public final Tier ownerTier;
    public final int version;
    public final boolean isTransient;
    public final boolean isOnlineAccount;
    public final boolean isDataSource;
    public final boolean isThingEngine;

    public DeviceInfo(JSONObject json) throws JSONException {
        uniqueId = json.getString("uniqueId");
        kind = json.getString("kind");
        name = json.getString("name");
        description = json.getString("description");
        ownerTier = Tier.valueOf(json.getString("ownerTier").toUpperCase());
        version = json.getInt("version");
        isTransient = json.getBoolean("isTransient");
        isOnlineAccount = json.getBoolean("isOnlineAccount");
        isDataSource = json.getBoolean("isDataSource");
        isThingEngine = json.getBoolean("isThingEngine");
    }

    public boolean isSame(DeviceInfo info) {
        return (this.uniqueId.equals(info.uniqueId) &&
                this.kind.equals(info.kind) &&
                this.name.equals(info.name) &&
                this.description.equals(info.description) &&
                this.ownerTier == info.ownerTier &&
                this.version == info.version &&
                this.isTransient == info.isTransient &&
                this.isOnlineAccount == info.isOnlineAccount &&
                this.isDataSource == info.isDataSource &&
                this.isThingEngine == info.isThingEngine);
    }
}
