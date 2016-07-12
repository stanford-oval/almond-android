package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import java.util.List;

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.DeviceInfo;

public class MyStuffFragment extends Fragment {
    private static final int REQUEST_CREATE_DEVICE = 2;

    private MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };
    private FragmentEmbedder mListener;

    private ArrayAdapter<DeviceInfo> mDevices;
    private ArrayAdapter<DeviceInfo> mAccounts;

    public MyStuffFragment() {}

    public static MyStuffFragment newInstance() {
        MyStuffFragment fragment = new MyStuffFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
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
            super(getActivity(), 0);
        }

        private String getIcon(DeviceInfo device) {
            return Config.S3_CLOUDFRONT_HOST + "/icons/" + device.kind + ".png";
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

            new LoadImageTask(icon).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getIcon(device));
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
            int eigthy_dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80.f, getResources().getDisplayMetrics()));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(eigthy_dp, eigthy_dp);
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

            new LoadImageTask(icon).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getIcon(device));
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
            Intent intent = new Intent(getActivity(), DeviceConfigureChooseKindActivity.class);
            intent.setAction(DeviceConfigureChooseKindActivity.ACTION);
            intent.putExtra("extra.CLASS", _class);

            startActivityForResult(intent, REQUEST_CREATE_DEVICE);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mDevices = new DeviceArrayAdapter();
        mAccounts = new DeviceArrayAdapter();

        ListAdapter[] adapters = new ListAdapter[] { mDevices, mAccounts };
        int[] view_ids = new int[] { R.id.my_devices_view, R.id.my_accounts_view };
        for (int i = 0; i < view_ids.length; i++) {
            ListAdapter adapter = adapters[i];
            GridView view = (GridView) getActivity().findViewById(view_ids[i]);

            view.setAdapter(adapter);
            view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    DeviceInfo device = (DeviceInfo) parent.getAdapter().getItem(position);

                    String _class = device.isDataSource ? "data" : (device.isOnlineAccount ? "online" : "physical");
                    Intent intent = new Intent(getActivity(), DeviceDetailsActivity.class);
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
            Button btn = (Button) getActivity().findViewById(button_ids[i]);
            String _class = classes[i];

            btn.setOnClickListener(new OnCreateButtonClicked(_class));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mEngine.addEngineReadyCallback(mReadyCallback);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();

        mEngine.removeEngineReadyCallback(mReadyCallback);
    }

    public void refresh() {
        new RefreshDevicesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void processDevices(Collection<DeviceInfo> devices) {
        mDevices.clear();
        mAccounts.clear();

        for (DeviceInfo device : devices) {
            if (device.isThingEngine)
                continue;
            if (device.isDataSource)
                continue; // we don't have space to show data sources, so we ignore them
            if (device.isOnlineAccount)
                mAccounts.add(device);
            else
                mDevices.add(device);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_stuff, container, false);
    }

    // this version of onAttach is deprecated but it's required
    // on APIs older than 23 because otherwise onAttach is never called
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof FragmentEmbedder) {
            mListener = (FragmentEmbedder) activity;
            mEngine = mListener.getEngine();
        } else {
            throw new RuntimeException(activity.toString()
                    + " must implement FragmentEmbedder");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        mEngine = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CREATE_DEVICE) {
            // do something with it
        }
    }
}
