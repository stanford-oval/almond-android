// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.ui;


import android.net.Uri;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.stanford.thingengine.engine.service.ControlBinder;

/**
 * Created by gcampagn on 6/4/17.
 */

public class DeviceHandleOAuth2Callback extends AsyncTask<Uri, Void, Exception> {
    private final Map<String, String> session;
    private final EngineServiceConnection engine;

    public DeviceHandleOAuth2Callback(EngineServiceConnection engine, Map<String, String> session) {
        this.engine = engine;
        this.session = session;
    }

    private static JSONObject mapToJson(Map<String, String> map) throws JSONException {
        JSONObject o = new JSONObject();

        for (Map.Entry<String, String> e : map.entrySet())
            o.put(e.getKey(), e.getValue());

        return o;
    }

    @Override
    protected Exception doInBackground(Uri... params) {
        try {
            Uri url = params[0];
            String kind = url.getLastPathSegment();

            ControlBinder control = engine.getControl();
            if (control != null) {
                try {
                    control.handleOAuth2Callback(kind, url.toString(), mapToJson(session));
                    return null;
                } catch (Exception e) {
                    return e;
                }
            } else {
                return null;
            }
        } catch(JSONException e) {
            return e;
        }
    }
}
