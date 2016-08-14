package edu.stanford.thingengine.engine.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
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
import java.util.Collections;
import java.util.List;

import edu.stanford.thingengine.engine.R;

public class DeviceConfigureChooseKindActivity extends Activity {
    public static final String ACTION = "edu.stanford.thingengine.engine.DEVICE_CHOOSE_KIND";

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
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private class GetFactoriesTask extends AsyncTask<String, Void, List<DeviceFactory>> {
        private List<DeviceFactory> processDevices(JSONArray devices, String _class) throws JSONException {
            List<DeviceFactory> factories = new ArrayList<>();

            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.getJSONObject(i);
                JSONObject jsonFactory = device.getJSONObject("factory");
                String kind = device.getString("primary_kind");
                String name = device.getString("name");

                DeviceFactory factory;

                switch (jsonFactory.getString("type")) {
                    case "form":
                        factory = new DeviceFactory.Form(name, kind, _class, DeviceFactory.FormControl.fromJSONArray(jsonFactory.getJSONArray("fields")));
                        break;

                    case "oauth2":
                        factory = new DeviceFactory.OAuth2(name, kind, _class);
                        break;

                    case "none":
                        factory = new DeviceFactory.None(name, kind, _class);
                        break;

                    case "discovery":
                        factory = new DeviceFactory.Discovery(name, kind, _class, jsonFactory.optString("discoveryType", null));
                        break;

                    default:
                        throw new JSONException("Invalid factory type");
                }

                factories.add(factory);
            }

            return factories;
        }

        @Override
        public List<DeviceFactory> doInBackground(String... params) {
            try {
                return processDevices(mThingpedia.getDeviceFactories(params[0]), params[0]);
            } catch (JSONException | IOException e) {
                Log.e(MainActivity.LOG_TAG, "Unexpected failure retrieving device factories", e);
                return Collections.emptyList();
            }
        }

        @Override
        public void onPostExecute(List<DeviceFactory> factories) {
            addAdapter(factories);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();

        if (intent == null) {
            finish();
            return;
        }

        String _class = intent.getStringExtra("extra.CLASS");
        if (_class == null)
            _class = "physical";

        switch (_class) {
            case "online":
                setTitle(R.string.create_account_title);
                break;
            case "data":
                setTitle(R.string.create_datasource_title);
                break;
            case "physical":
                setTitle(R.string.create_device_title);
                break;
        }

        new GetFactoriesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, _class);
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

    private void addAdapter(List<DeviceFactory> factories) {
        ListAdapter adapter = new DeviceCreateButtonAdapter(factories);
        ListView listView = (ListView) findViewById(R.id.device_choose_kind_container);
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
                btn = (Button) convertView;
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
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
