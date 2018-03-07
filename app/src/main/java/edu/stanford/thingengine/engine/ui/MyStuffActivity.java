// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.DeviceInfo;

public class MyStuffActivity extends Activity {
    private static final int REQUEST_CREATE_DEVICE = 2;

    private final MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    private ArrayAdapter<DeviceInfo> mDevices;

    public MyStuffActivity() {
        mEngine = new MainServiceConnection();
    }

    private class RefreshDevicesTask extends AsyncTask<Void, Void, List<DeviceInfo>> {
        @Override
        public List<DeviceInfo> doInBackground(Void... params) {
            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return Collections.emptyList();

                return control.getDeviceInfos();
            } catch(Exception e) {
                Log.e(MainActivity.LOG_TAG, "Failed to retrieve device list", e);
                return Collections.emptyList();
            }
        }

        @Override
        public void onPostExecute(List<DeviceInfo> devices) {
            processDevices(devices);
        }
    }

    private class DeviceArrayAdapter extends ArrayAdapter<DeviceInfo> {
        public DeviceArrayAdapter() {
            super(MyStuffActivity.this, 0);
        }

        private String getIcon(DeviceInfo device) {
            return BuildConfig.THINGPEDIA_URL + "/api/devices/icon/" + device.kind;
        }

        private boolean tryConvert(View convertView, DeviceInfo device) {
            if (!(convertView instanceof LinearLayout))
                return false;

            LinearLayout linearLayout = (LinearLayout)convertView;
            View firstChild = linearLayout.getChildAt(0);
            if (!(firstChild instanceof ImageView))
                return false;
            ImageView icon = (ImageView)firstChild;

            View secondChild = linearLayout.getChildAt(1);
            if (!(secondChild instanceof TextView))
                return false;

            TextView name = (TextView)secondChild;

            LoadImageTask.load(MyStuffActivity.this, icon, getIcon(device));
            name.setText(device.name);
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeviceInfo device = getItem(position);
            if (tryConvert(convertView, device))
                return convertView;

            LinearLayout linearLayout = new LinearLayout(getContext());
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            ImageView icon = new ImageView(getContext());
            int sixty_dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60.f, getResources().getDisplayMetrics()));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(sixty_dp, sixty_dp);
            iconParams.gravity = Gravity.CENTER_HORIZONTAL;
            int ten_dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10.f, getResources().getDisplayMetrics()));
            iconParams.bottomMargin = ten_dp;
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            linearLayout.addView(icon, iconParams);

            TextView text = new TextView(getContext());
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            text.setGravity(Gravity.CENTER_HORIZONTAL);
            textParams.gravity = Gravity.CENTER_HORIZONTAL;
            linearLayout.addView(text, textParams);

            LoadImageTask.load(MyStuffActivity.this, icon, getIcon(device));
            text.setText(device.name);

            return linearLayout;
        }
    }

    private class OnCreateButtonClicked implements View.OnClickListener {
        private final String _class;

        public OnCreateButtonClicked(String _class) {
            this._class = _class;
        }

        @Override
        public void onClick(View view) {
            Intent intent = new Intent(MyStuffActivity.this, DeviceConfigureChooseKindActivity.class);
            intent.setAction(DeviceConfigureChooseKindActivity.ACTION);
            intent.putExtra("extra.CLASS", _class);

            startActivityForResult(intent, REQUEST_CREATE_DEVICE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_stuff);

        mDevices = new DeviceArrayAdapter();

        ListAdapter[] adapters = new ListAdapter[] { mDevices };
        int[] view_ids = new int[] { R.id.my_devices_view };
        for (int i = 0; i < view_ids.length; i++) {
            ListAdapter adapter = adapters[i];
            GridView view = (GridView) this.findViewById(view_ids[i]);

            view.setAdapter(adapter);
            view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    DeviceInfo device = (DeviceInfo) parent.getAdapter().getItem(position);

                    String _class = device.isDataSource ? "data" : (device.isOnlineAccount ? "online" : "physical");
                    Intent intent = new Intent(MyStuffActivity.this, DeviceDetailsActivity.class);
                    intent.setAction(DeviceDetailsActivity.ACTION);
                    intent.putExtra("extra.INFO", device);
                    intent.putExtra("extra.CLASS", _class);

                    startActivity(intent);
                }
            });
        }

        int[] button_ids = new int[] { R.id.btn_create_device, R.id.btn_create_account };
        String[] classes = new String[] { "physical", "online" };

        for (int i = 0; i < classes.length; i++) {
            Button btn = (Button) findViewById(button_ids[i]);
            String _class = classes[i];

            btn.setOnClickListener(new OnCreateButtonClicked(_class));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mEngine.start(this);
        mEngine.addEngineReadyCallback(mReadyCallback);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        mEngine.removeEngineReadyCallback(mReadyCallback);
        mEngine.stop(this);
    }

    public void refresh() {
        new RefreshDevicesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void processDevices(Collection<DeviceInfo> devices) {
        mDevices.clear();

        for (DeviceInfo device : devices) {
            if (device.isThingEngine)
                continue;
            mDevices.add(device);
        }

        mDevices.sort(new Comparator<DeviceInfo>() {
            @Override
            public int compare(DeviceInfo lhs, DeviceInfo rhs) {
                return lhs.name.compareTo(rhs.name);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CREATE_DEVICE) {
            // do something with it
        }
    }
}
