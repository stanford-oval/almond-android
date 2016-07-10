package edu.stanford.thingengine.engine.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class DeviceCreateFormActivity extends Activity {
    public static final String ACTION = "edu.stanford.thingengine.engine.DEVICE_CREATE";

    private String mKind;
    private String mClass;
    private ArrayAdapter<DeviceFactory.FormControl> mControls;
    private final EngineServiceConnection mEngine;

    public DeviceCreateFormActivity() {
        mEngine = new EngineServiceConnection();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_create_form);

        mControls = new DeviceFormControlsAdapter();
        ListView listView = (ListView)findViewById(R.id.device_create_form_container);
        listView.setAdapter(mControls);

        Button btn = (Button)findViewById(R.id.button_configure);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmConfigure();
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

        mKind = intent.getStringExtra("extra.KIND");
        mClass = intent.getStringExtra("extra.CLASS");
        if (mClass == null)
            mClass = "physical";
        String title = intent.getStringExtra("extra.TITLE");
        if (title != null) {
            setTitle(title);
        } else {
            switch (mClass) {
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
        }
        List<DeviceFactory.FormControl> controls = new ArrayList<>();
        Collection<?> obtained = (Collection<?>) intent.getSerializableExtra("extra.CONTROLS");
        for (Object o : obtained)
            controls.add((DeviceFactory.FormControl)o);
        mControls.addAll(controls);


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

    private class DeviceFormControlsAdapter extends ArrayAdapter<DeviceFactory.FormControl> {
        DeviceFormControlsAdapter() {
            super(DeviceCreateFormActivity.this, 0, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final DeviceFactory.FormControl control = getItem(position);

            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.FILL_HORIZONTAL);

            TextView textView = new TextView(getContext());
            textView.setText(control.label);
            layout.addView(textView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            EditText editText = new EditText(getContext());
            switch (control.type) {
                case "password":
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                    break;
                case "email":
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
                    break;
                case "number":
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    break;
                case "text":
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    break;
            }
            editText.setGravity(Gravity.FILL_HORIZONTAL);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.FILL_HORIZONTAL;
            layout.addView(editText, layoutParams);

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (control.type.equals("number"))
                        control.value = Integer.valueOf(s.toString());
                    else
                        control.value = s.toString();
                }
            });

            return layout;
        }
    }

    private void confirmConfigure() {
        int count = mControls.getCount();
        final JSONObject obj = new JSONObject();

        try {
            obj.put("kind", mKind);
            for (int i = 0; i < count; i++) {
                DeviceFactory.FormControl control = mControls.getItem(i);
                obj.put(control.name, control.value);
            }
        } catch (JSONException e) {
            DialogUtils.showFailureDialog(this, "Failed to create device: " + e.getMessage());
            return;
        }

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;

                try {
                    control.createDevice(obj);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                } catch(final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DialogUtils.showFailureDialog(DeviceCreateFormActivity.this, "Failed to create device: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void navigateUp() {
        Intent intent = getParentActivityIntent();
        if (intent != null)
            intent.putExtra("extra.CLASS", mClass);
        NavUtils.navigateUpTo(this, intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateUp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
