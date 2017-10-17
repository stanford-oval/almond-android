package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.Collections;
import java.util.List;

import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.PermissionInfo;

/**
 * Created by gcampagn on 10/13/17.
 */

public class MyPermissionsActivity extends Activity {
    private final MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    private ArrayAdapter<PermissionInfo> mPermissions;

    public MyPermissionsActivity() {
        mEngine = new MainServiceConnection();
    }

    private class RefreshPermissionsTask extends AsyncTask<Void, Void, List<PermissionInfo>> {
        @Override
        public List<PermissionInfo> doInBackground(Void... params) {
            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return Collections.emptyList();

                return control.getAllPermissions();
            } catch(Exception e) {
                Log.e(MainActivity.LOG_TAG, "Failed to retrieve permission list", e);
                return Collections.emptyList();
            }
        }

        @Override
        public void onPostExecute(List<PermissionInfo> devices) {
            processPermissions(devices);
        }
    }

    private class RevokePermissionTask extends AsyncTask<String, Void, Exception> {
        @Override
        protected Exception doInBackground(String... params) {
            String uniqueId = params[0];

            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return null;

                control.revokePermission(uniqueId);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        public void onPostExecute(Exception e) {
            if (e != null) {
                DialogUtils.showAlertDialog(MyPermissionsActivity.this, "Failed to revoke permission: " + e.getMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
            } else {
                refresh();
            }
        }
    }

    private void revokePermission(String uniqueId) {
        new RevokePermissionTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uniqueId);
    }

    private void maybeRevokePermission(final String uniqueId) {
        DialogUtils.showConfirmDialog(MyPermissionsActivity.this, "Do you wish to revoke this permission?", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                revokePermission(uniqueId);
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
        setContentView(R.layout.activity_permissions);

        mPermissions = new ArrayAdapter<>(this, R.layout.layout_text_only, R.id.app_description);
        ListView list = (ListView) findViewById(R.id.app_list);
        list.setAdapter(mPermissions);

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                PermissionInfo perm = (PermissionInfo) parent.getAdapter().getItem(position);
                maybeRevokePermission(perm.uniqueId);
                return true;
            }
        });
    }

    private void processPermissions(List<PermissionInfo> apps) {
        mPermissions.clear();
        mPermissions.addAll(apps);
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
        new RefreshPermissionsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
