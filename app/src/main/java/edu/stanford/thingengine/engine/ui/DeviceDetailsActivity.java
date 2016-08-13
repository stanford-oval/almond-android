package edu.stanford.thingengine.engine.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.DeviceInfo;

public class DeviceDetailsActivity extends Activity {
    public static final String ACTION = "edu.stanford.thingengine.engine.DEVICE_DETAILS";

    private final EngineServiceConnection mEngine;
    private DeviceInfo mDeviceInfo;
    private String mUniqueId;
    private String mClass;
    private boolean mCheckingAvailable;

    public DeviceDetailsActivity() {
        mEngine = new EngineServiceConnection();
    }

    private class DeleteDeviceTask extends AsyncTask<String, Void, Exception> {
        @Override
        protected Exception doInBackground(String... params) {
            String uniqueId = params[0];

            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return null;

                control.deleteDevice(uniqueId);
                return null;
            } catch(Exception e) {
                return e;
            }
        }

        @Override
        public void onPostExecute(Exception e) {
            if (e != null) {
                DialogUtils.showAlertDialog(DeviceDetailsActivity.this, "Failed to delete device: " + e.getMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                });
            } else {
                finish();
            }
        }
    }

    private void maybeDeleteDevice() {
        DialogUtils.showConfirmDialog(this, "Are you sure?", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new DeleteDeviceTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mUniqueId);
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });
    }

    private class UpgradeDeviceTask extends AsyncTask<String, Void, Exception> {
        @Override
        protected Exception doInBackground(String... params) {
            String kind = params[0];

            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return null;

                control.upgradeDevice(kind);
                return null;
            } catch(Exception e) {
                return e;
            }
        }

        @Override
        public void onPostExecute(Exception e) {
            if (e != null) {
                DialogUtils.showAlertDialog(DeviceDetailsActivity.this, "Failed to upgrade device: " + e.getMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                });
            } else {
                refresh();
            }
        }
    }

    private void upgrade() {
        new UpgradeDeviceTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mDeviceInfo.kind);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);

        Button deleteBtn = (Button) findViewById(R.id.btn_delete_device);
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                maybeDeleteDevice();
            }
        });

        Button refreshBtn = (Button) findViewById(R.id.btn_refresh_device);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        Button upgradeBtn = (Button) findViewById(R.id.btn_upgrade_device);
        upgradeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                upgrade();
            }
        });

        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent == null || !ACTION.equals(intent.getAction())) {
            finish();
            return;
        }

        mDeviceInfo = (DeviceInfo) intent.getSerializableExtra("extra.INFO");
        mUniqueId = mDeviceInfo.uniqueId;

        mClass = intent.getStringExtra("extra.CLASS");
        Button deleteBtn = (Button) findViewById(R.id.btn_delete_device);
        switch (mClass) {
            case "online":
                deleteBtn.setText(R.string.btn_delete_account);
                break;
            case "data":
                deleteBtn.setText(R.string.btn_delete_datasource);
                break;
            case "physical":
                deleteBtn.setText(R.string.btn_delete_device);
                break;
        }

        fillView();
        maybeCheckAvailable();
    }

    private class RefreshTask extends AsyncTask<String, Void, DeviceInfo> {
        @Override
        protected DeviceInfo doInBackground(String... params) {
            String uniqueId = params[0];

            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return null;

                return control.getDeviceInfo(uniqueId);
            } catch(Exception e) {
                Log.e(MainActivity.LOG_TAG, "Failed to refresh device", e);
                return null;
            }
        }

        @Override
        public void onPostExecute(DeviceInfo info) {
            if (info == null)
                return;

            mDeviceInfo = info;
            fillView();
        }
    }

    private void fillView() {
        setTitle(mDeviceInfo.name);

        ImageView icon = (ImageView) findViewById(R.id.device_icon);
        new LoadImageTask(this, icon).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, Config.S3_CLOUDFRONT_HOST + "/icons/" + mDeviceInfo.kind + ".png");

        TextView description = (TextView) findViewById(R.id.device_description);
        description.setText(mDeviceInfo.description);

        View deleteBtn = findViewById(R.id.btn_delete_device);
        deleteBtn.setVisibility(mDeviceInfo.isTransient ? View.GONE : View.VISIBLE);

        View status = findViewById(R.id.device_status);
        status.setVisibility((mDeviceInfo.isOnlineAccount || mDeviceInfo.isDataSource) ? View.GONE : View.VISIBLE);

        boolean isBuiltin = mDeviceInfo.kind.startsWith("org.thingpedia.builtin");

        TextView version = (TextView) findViewById(R.id.device_version);
        version.setText(getString(R.string.device_version, mDeviceInfo.version));
        version.setVisibility(isBuiltin ? View.GONE : View.VISIBLE);
        View upgradeBtn = findViewById(R.id.btn_upgrade_device);
        upgradeBtn.setVisibility(isBuiltin ? View.GONE : View.VISIBLE);
    }

    public void refresh() {
        new RefreshTask().execute(mUniqueId);
        maybeCheckAvailable();
    }

    private interface Availability {
        int UNAVAILABLE = 0;
        int AVAILABLE = 1;
        int OWNER_UNAVAILABLE = 2;
        int UNKNOWN = -1;
    }

    private class CheckAvailableTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            String uniqueId = params[0];

            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return Availability.UNKNOWN;

                return control.checkDeviceAvailable(uniqueId);
            } catch(Exception e) {
                Log.e(MainActivity.LOG_TAG, "Failed to check for device availability", e);
                return Availability.UNKNOWN;
            }
        }

        @Override
        public void onPostExecute(Integer avail) {
            TextView status = (TextView) findViewById(R.id.device_status);

            switch (avail) {
                case Availability.AVAILABLE:
                    status.setText(R.string.device_available);
                    return;
                case Availability.OWNER_UNAVAILABLE:
                case Availability.UNAVAILABLE:
                    status.setText(R.string.device_unavailable);
                    return;
                case Availability.UNKNOWN:
                    status.setText(R.string.device_availability_unknown);
            }
        }
    }

    private void maybeCheckAvailable() {
        if (mCheckingAvailable)
            return;

        mCheckingAvailable = true;
        new CheckAvailableTask().execute(mUniqueId);
    }

    @Override
    public void onResume() {
        super.onResume();
        mEngine.start(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mEngine.stop(this);
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
