package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.os.Bundle;

import edu.stanford.thingengine.engine.R;

public class DeviceCreateFormActivity extends Activity {
    public static final String ACTION = "edu.stanford.thingengine.engine.DEVICE_CREATE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_create_form);
    }
}
