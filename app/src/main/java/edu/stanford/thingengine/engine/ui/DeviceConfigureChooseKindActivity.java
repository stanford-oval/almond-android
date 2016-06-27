package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.thingengine.engine.R;

public class DeviceConfigureChooseKindActivity extends Activity {
    public static final String ACTION = "edu.stanford.thingengine.engine.DEVICE_CHOOSE_KIND";

    private String mClass;

    private final EngineServiceConnection mEngine;
    private final ThingpediaClient mThingpedia;

    public DeviceConfigureChooseKindActivity() {
        mEngine = new EngineServiceConnection();
        mThingpedia = new ThingpediaClient(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_configure_choose_kind);
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();

        if (intent == null || !ACTION.equals(intent.getAction())) {
            finish();
            return;
        }

        mClass = intent.getStringExtra("extra.CLASS");

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray devices = mThingpedia.getDeviceFactories(mClass);
                    final List<DeviceFactory> factories = processDevices(devices);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addAdapter(factories);
                        }
                    });
                } catch(JSONException|IOException e) {
                    Log.e(MainActivity.LOG_TAG, "Unexpected failure retrieving device factories", e);
                }
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        mEngine.start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEngine.stop(this);
    }

    private static List<DeviceFactory.FormControl> jsonFormToMap(JSONArray array) throws JSONException {
        List<DeviceFactory.FormControl> list = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject field = array.getJSONObject(i);
            DeviceFactory.FormControl control = new DeviceFactory.FormControl();
            control.label = field.getString("label");
            control.type = field.getString("type");
            control.name = field.getString("name");
            list.add(control);
        }

        return list;
    }

    private List<DeviceFactory> processDevices(JSONArray devices) throws JSONException {
        List<DeviceFactory> factories = new ArrayList<>();

        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.getJSONObject(i);
            JSONObject jsonFactory = device.getJSONObject("factory");
            String kind = device.getString("primary_kind");
            String name = device.getString("name");

            DeviceFactory factory;

            switch (jsonFactory.getString("type")) {
                case "form":
                    factory = new DeviceFactory.Form(name, kind, jsonFormToMap(jsonFactory.getJSONArray("fields")));
                    break;

                case "oauth2":
                    factory = new DeviceFactory.OAuth2(name, kind);
                    break;

                case "none":
                    factory = new DeviceFactory.None(name, kind);
                    break;

                default:
                    throw new JSONException("Invalid factory type");
            }

            factories.add(factory);
        }

        return factories;
    }

    private void addAdapter(List<DeviceFactory> factories) {
        ListAdapter adapter = new DeviceCreateButtonAdapter(factories);
        ListView listView = (ListView)findViewById(R.id.device_choose_kind_container);
        listView.setAdapter(adapter);
    }

    private class DeviceCreateButtonAdapter extends ArrayAdapter<DeviceFactory> {
        DeviceCreateButtonAdapter(List<DeviceFactory> factories) {
            super(DeviceConfigureChooseKindActivity.this, 0, 0, factories);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final DeviceFactory factory = getItem(position);

            Button btn;
            if (convertView != null && convertView instanceof Button)
                btn = (Button)convertView;
            else
                btn = new Button(getContext());

            btn.setText(factory.getName());
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    factory.activate(DeviceConfigureChooseKindActivity.this, mEngine);
                }
            });

            return btn;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == DeviceFactory.REQUEST_OAUTH2) {
            setResult(resultCode);
            finish();
            return;
        }
    }
}
