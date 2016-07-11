package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.os.AsyncTask;

import org.json.JSONObject;

import edu.stanford.thingengine.engine.service.ControlBinder;

/**
 * Created by gcampagn on 7/11/16.
 */
class DeviceConfigureTask extends AsyncTask<JSONObject, Void, Exception> {
    private final Activity mActivity;
    private final EngineServiceConnection mEngine;

    public DeviceConfigureTask(Activity activity, EngineServiceConnection engine) {
        mActivity = activity;
        mEngine = engine;
    }

    @Override
    public Exception doInBackground(JSONObject... params) {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return null;

        try {
            control.createDevice(params[0]);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    @Override
    public void onPostExecute(Exception e) {
        if (e == null) {
            mActivity.setResult(Activity.RESULT_OK);
            mActivity.finish();
        } else {
            DialogUtils.showFailureDialog(mActivity, "Failed to create device: " + e.getMessage());
        }
    }
}
