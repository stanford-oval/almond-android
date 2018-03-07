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

    private static JSONObject queryToJson(Uri uri) throws JSONException {
        Collection<String> names = uri.getQueryParameterNames();
        JSONObject o = new JSONObject();

        for (String name : names) {
            List<String> values = uri.getQueryParameters(name);

            if (values.size() == 1) {
                o.put(name, values.get(0));
            } else {
                JSONArray a = new JSONArray();
                for (String value : values)
                    a.put(value);
                o.put(name, a);
            }
        }

        return o;
    }

    @Override
    protected Exception doInBackground(Uri... params) {
        try {
            Uri url = params[0];
            String kind = url.getLastPathSegment();

            JSONObject req = new JSONObject();
            // there is no actual http request going on, so the values are fake
            // oauth modules should not rely on these anyway
            req.put("httpVersion", "1.0");
            req.put("headers", new JSONArray());
            req.put("rawHeaders", new JSONArray());

            req.put("method", "GET");
            req.put("url", url.toString());
            // body is always empty for a GET request!
            req.put("query", queryToJson(url));
            req.put("session", mapToJson(session));

            ControlBinder control = engine.getControl();
            if (control != null) {
                try {
                    control.handleOAuth2Callback(kind, req);
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
