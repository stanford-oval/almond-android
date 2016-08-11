package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gcampagn on 6/27/16.
 */
public abstract class DeviceFactory {
    public static final int REQUEST_OAUTH2 = 1;
    public static final int REQUEST_FORM = 2;

    protected final String name;
    protected final String kind;
    protected final String _class;

    protected DeviceFactory(String name, String kind, String _class) {
        this.name = name;
        this.kind = kind;
        this._class = _class;
    }

    public String getName() {
        return name;
    }

    public abstract void activate(Activity activity, EngineServiceConnection engine);

    public static class None extends DeviceFactory {
        public None(String name, String kind, String _class) {
            super(name, kind, _class);
        }

        @Override
        public void activate(Activity activity, EngineServiceConnection engine) {
            try {
                final JSONObject object = new JSONObject();
                object.put("kind", kind);
                new DeviceConfigureTask(activity, engine).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, object);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class OAuth2 extends DeviceFactory {
        public OAuth2(String name, String kind, String _class) {
            super(name, kind, _class);
        }

        @Override
        public void activate(Activity activity, EngineServiceConnection engine) {
            Intent intent = new Intent(activity, OAuthActivity.class);
            intent.setAction(OAuthActivity.ACTION);
            intent.putExtra("extra.KIND", kind);
            intent.putExtra("extra.CLASS", _class);
            intent.putExtra("extra.TITLE", name);

            activity.startActivityForResult(intent, REQUEST_OAUTH2);
        }
    }

    public static class FormControl implements Serializable {
        public String type;
        public String name;
        public String label;
        public Object value;

        public static FormControl fromJSON(JSONObject obj) throws JSONException {
            DeviceFactory.FormControl control = new DeviceFactory.FormControl();
            control.label = obj.getString("label");
            control.type = obj.getString("type");
            control.name = obj.getString("name");
            return control;
        }

        public static List<FormControl> fromJSONArray(JSONArray array) throws JSONException {
            List<DeviceFactory.FormControl> list = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject field = array.getJSONObject(i);
                list.add(DeviceFactory.FormControl.fromJSON(field));
            }

            return list;
        }
    }

    public static class Form extends DeviceFactory {
        private final List<FormControl> controls;

        public Form(String name, String kind, String _class, List<FormControl> controls) {
            super(name, kind, _class);
            this.controls = controls;
        }

        @Override
        public void activate(Activity activity, EngineServiceConnection engine) {
            Intent intent = new Intent(activity, DeviceCreateFormActivity.class);
            intent.setAction(DeviceCreateFormActivity.ACTION);
            intent.putExtra("extra.KIND", kind);
            intent.putExtra("extra.CLASS", _class);
            intent.putExtra("extra.TITLE", name);
            intent.putExtra("extra.CONTROLS", (Serializable)controls);

            activity.startActivityForResult(intent, REQUEST_FORM);
        }
    }
}
