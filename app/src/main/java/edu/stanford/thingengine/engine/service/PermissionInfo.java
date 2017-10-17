package edu.stanford.thingengine.engine.service;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by gcampagn on 10/13/17.
 */

public class PermissionInfo {
    public final String uniqueId;
    public final String description;

    public PermissionInfo(JSONObject object) throws JSONException {
        uniqueId = object.getString("uniqueId");
        description = object.getString("description");
    }

    // for convenience of ArrayAdapter
    @Override
    public String toString() {
        return description;
    }
}
