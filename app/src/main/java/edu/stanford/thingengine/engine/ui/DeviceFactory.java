package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;

import edu.stanford.thingengine.engine.service.ControlBinder;

/**
 * Created by gcampagn on 6/27/16.
 */
public abstract class DeviceFactory {
    public static final int REQUEST_OAUTH2 = 1;
    public static final int REQUEST_FORM = 2;

    private final String name;
    private final String kind;

    protected DeviceFactory(String name, String kind) {
        this.name = name;
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    protected String getKind() {
        return kind;
    }

    public abstract void activate(Activity activity, EngineServiceConnection engine);

    public static class None extends DeviceFactory {
        public None(String name, String kind) {
            super(name, kind);
        }

        @Override
        public void activate(final Activity activity, final EngineServiceConnection engine) {
            try {
                final JSONObject object = new JSONObject();
                object.put("kind", getKind());
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        ControlBinder control = engine.getControl();
                        if (control == null)
                            return;

                        try {
                            control.createDevice(object);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    activity.setResult(Activity.RESULT_OK);
                                    activity.finish();
                                }
                            });
                        } catch(final Exception e) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    DialogUtils.showFailureDialog(activity, "Failed to create device: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class OAuth2 extends DeviceFactory {
        public OAuth2(String name, String kind) {
            super(name, kind);
        }

        @Override
        public void activate(Activity activity, EngineServiceConnection engine) {
            Intent intent = new Intent(activity, OAuthActivity.class);
            intent.setAction(OAuthActivity.ACTION);
            intent.putExtra("extra.KIND", getKind());

            activity.startActivityForResult(intent, REQUEST_OAUTH2);
        }
    }

    public static class FormControl implements Serializable {
        public String type;
        public String name;
        public String label;
        public Object value;
    }

    public static class Form extends DeviceFactory {
        private final List<FormControl> controls;

        public Form(String name, String kind, List<FormControl> controls) {
            super(name, kind);
            this.controls = controls;
        }

        @Override
        public void activate(Activity activity, EngineServiceConnection engine) {
            Intent intent = new Intent(activity, DeviceCreateFormActivity.class);
            intent.setAction(DeviceCreateFormActivity.ACTION);
            intent.putExtra("extra.KIND", getKind());
            intent.putExtra("extra.CONTROLS", (Serializable)controls);

            activity.startActivityForResult(intent, REQUEST_FORM);
        }
    }
}
