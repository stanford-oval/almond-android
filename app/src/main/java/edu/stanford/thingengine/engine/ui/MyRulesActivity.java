package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;

import java.util.Collections;
import java.util.List;

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AppInfo;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class MyRulesActivity extends Activity {
    private final MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    private ArrayAdapter<AppInfo> mApps;

    public MyRulesActivity() {
        mEngine = new MainServiceConnection();
    }

    private class RefreshAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {
        @Override
        public List<AppInfo> doInBackground(Void... params) {
            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return Collections.emptyList();

                return control.getAppInfos();
            } catch(Exception e) {
                Log.e(MainActivity.LOG_TAG, "Failed to retrieve app list", e);
                return Collections.emptyList();
            }
        }

        @Override
        public void onPostExecute(List<AppInfo> devices) {
            processApps(devices);
        }
    }

    private class StopAppTask extends AsyncTask<String, Void, Exception> {
        @Override
        protected Exception doInBackground(String... params) {
            String uniqueId = params[0];

            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return null;

                control.deleteApp(uniqueId);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        public void onPostExecute(Exception e) {
            if (e != null) {
                DialogUtils.showAlertDialog(MyRulesActivity.this, "Failed to stop rule: " + e.getMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
            } else {
                refresh();
            }
        }
    }

    private void stopApp(String uniqueId) {
        new StopAppTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uniqueId);
    }

    private void maybeStopApp(final String uniqueId) {
        DialogUtils.showConfirmDialog(MyRulesActivity.this, "Do you wish to stop this rule?", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopApp(uniqueId);
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_rules);

        mApps = new ArrayAdapter<AppInfo>(this, R.layout.layout_single_app, R.id.app_description) {
            @Override
            public View getView(int position, View recycleView, ViewGroup parent) {
                View created = super.getView(position, recycleView, parent);

                AppInfo app = getItem(position);
                if (app.icon != null)
                    LoadImageTask.load(getContext(), (ImageView)created.findViewById(R.id.app_icon), Config.S3_CLOUDFRONT_HOST + "/icons/" + app.icon + ".png");

                return created;
            }
        };
        ListView list = (ListView) findViewById(R.id.app_list);
        list.setAdapter(mApps);

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = (AppInfo) parent.getAdapter().getItem(position);
                maybeStopApp(app.uniqueId);
                return true;
            }
        });

        Button btn = (Button) findViewById(R.id.btn_create_rule);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;

                control.getAssistant().handleMakeRule();
                Intent intent = new Intent(MyRulesActivity.this, MainActivity.class);
                startActivity(intent);
                return;
            }
        });
    }

    private void processApps(List<AppInfo> apps) {
        mApps.clear();
        mApps.addAll(apps);
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
        mEngine.stop(this);
        mEngine.removeEngineReadyCallback(mReadyCallback);
    }

    public void refresh() {
        new RefreshAppsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
