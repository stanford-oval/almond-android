// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.ui;


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;
import net.hockeyapp.android.metrics.MetricsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.Serializable;
import java.util.Map;

import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantDispatcher;
import edu.stanford.thingengine.engine.service.AssistantLifecycleCallbacks;
import edu.stanford.thingengine.engine.service.AssistantMessage;
import edu.stanford.thingengine.engine.service.AssistantOutput;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class MainActivity extends Activity implements AssistantOutput, AssistantLifecycleCallbacks, ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String LOG_TAG = "thingengine.UI";
    static final int REQUEST_OAUTH2 = 1;
    static final int REQUEST_CREATE_DEVICE = 2;
    static final int REQUEST_LOCATION = 3;
    static final int REQUEST_ENABLE_PLAY_SERVICES = 4;
    static final int REQUEST_PICTURE = 5;
    static final int REQUEST_AUDIO_PERMISSION = 6;
    static final int REQUEST_EMAIL = 7;
    static final int REQUEST_PHONE_NUMBER = 8;

    private static final boolean ENABLE_AUTOCOMPLETION = false;

    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            ControlBinder control = engine.getControl();
            if (control == null)
                return;

            AssistantDispatcher assistant = control.getAssistant();
            assistant.setAssistantOutput(MainActivity.this);
            assistant.setAssistantCallbacks(MainActivity.this);
            mListAdapter.setHistory(assistant.getHistory());
            assistant.ready();
        }
    };

    private AssistantHistoryAdapter mListAdapter = new AssistantHistoryAdapter(MainActivity.this);
    private boolean mInCommand = false;
    private boolean mIsPassword = false;

    private final MainServiceConnection engine;

    public MainActivity() {
        engine = new MainServiceConnection();
    }

    public MainServiceConnection getEngine() {
        return engine;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);

        setContentView(R.layout.activity_main);

        mListAdapter.setContext(this);
        final AutoCompleteTextView input = (AutoCompleteTextView) findViewById(R.id.assistant_input);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_NULL) {
                    onTextActivated(false);
                    return true;
                } else {
                    return false;
                }
            }
        });
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setThreshold(1);
        if (ENABLE_AUTOCOMPLETION) {
            input.setAdapter(new AutoCompletionAdapter(new ThingpediaClient(this), this));
            input.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    AutoCompletionAdapter adapter = (AutoCompletionAdapter) parent.getAdapter();
                    onAutoCompletionClicked(adapter.getItem(position));
                    input.setText("");
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            input.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    AutoCompletionAdapter adapter = (AutoCompletionAdapter) parent.getAdapter();
                    onAutoCompletionClicked(adapter.getItem(position));
                    input.setText("");
                }
            });
        }

        View cancelbtn = findViewById(R.id.btn_cancel);
        cancelbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleNeverMind();
            }
        });

        final RecyclerView chatList = ((RecyclerView) findViewById(R.id.chat_list));

        chatList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideKeyboard(v);
                showInput();
                return false;
            }
        });

        chatList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (oldBottom - bottom > 100 && input.hasFocus()) {
                    chatList.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListAdapter.getItemCount() > 0)
                                chatList.smoothScrollToPosition(mListAdapter.getItemCount() - 1);
                        }
                    });
                }
            }
        });

        chatList.setAdapter(mListAdapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.setSmoothScrollbarEnabled(false);
        chatList.setLayoutManager(layoutManager);

        findViewById(R.id.assistant_progress).setVisibility(View.GONE);

        UpdateManager.register(this);
        if (!BuildConfig.DEBUG) {
            MetricsManager.register(this, getApplication());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        engine.start(this);
        engine.addEngineReadyCallback(mReadyCallback);
        mReadyCallback.run();
        CrashManager.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        ControlBinder control = engine.getControl();
        if (control != null) {
            AssistantDispatcher assistant = control.getAssistant();
            assistant.setAssistantOutput(null);
            assistant.setAssistantCallbacks(null);
        }
        engine.removeEngineReadyCallback(mReadyCallback);
        engine.stop(this);
        UpdateManager.unregister();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mListAdapter.setHistory(null);
        mListAdapter.setContext(null);
    }

    private boolean mScrollScheduled;

    private void syncCancelButton(AssistantMessage.AskSpecial msg) {
        final boolean visible = msg.what != AssistantMessage.AskSpecialType.NULL;
        findViewById(R.id.btn_cancel).setVisibility(visible ? View.VISIBLE : View.GONE);
    }


    private void syncSuggestions(AssistantMessage.AskSpecial msg) {
        final boolean visible = msg.what != AssistantMessage.AskSpecialType.NULL;
        ((AutoCompleteTextView) findViewById(R.id.assistant_input)).setThreshold(visible? 1000 : 1);
    }

    private void syncKeyboardType(AssistantMessage.AskSpecial msg) {
        int type;

        switch (msg.what) {
            case NUMBER:
                type = InputType.TYPE_CLASS_NUMBER;
                break;
            case EMAIL_ADDRESS:
                type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                break;
            case PHONE_NUMBER:
                type = InputType.TYPE_CLASS_PHONE;
                break;
            case PASSWORD:
                type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
                break;
            default:
                type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                break;
        }
        ((TextView) findViewById(R.id.assistant_input)).setInputType(type);
        mIsPassword = msg.what == AssistantMessage.AskSpecialType.PASSWORD;
    }

    public void display(AssistantMessage msg) {
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL) {
            syncSuggestions((AssistantMessage.AskSpecial) msg);
            syncCancelButton((AssistantMessage.AskSpecial) msg);
            syncKeyboardType((AssistantMessage.AskSpecial) msg);
        }
        showInput();
        scheduleScroll();
    }

    void scheduleScroll() {
        if (mScrollScheduled)
            return;
        mScrollScheduled = true;

        final RecyclerView listView = (RecyclerView) findViewById(R.id.chat_list);
        listView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScrollScheduled = false;
                if (mListAdapter.getItemCount() > 0)
                    listView.smoothScrollToPosition(mListAdapter.getItemCount() - 1);
            }
        }, 500);
    }

    void showLocationPicker() {
        try {
            Intent pickerIntent = new PlacePicker.IntentBuilder().build(this);
            startActivityForResult(pickerIntent, REQUEST_LOCATION);
        } catch (GooglePlayServicesNotAvailableException e) {
            DialogUtils.showAlertDialog(this, "Google Play Services is not available", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        } catch (GooglePlayServicesRepairableException e) {
            GoogleApiAvailability.getInstance().getErrorDialog(this,
                    e.getConnectionStatusCode(), REQUEST_ENABLE_PLAY_SERVICES);
        }
    }

    void showImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_PICTURE);
    }

    void showContactPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        if (requestCode == REQUEST_PHONE_NUMBER)
            intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        else
            intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_TYPE);
        startActivityForResult(intent, requestCode);
    }

    private void onAutoCompletionClicked(final AutoCompletionAdapter.Item item) {
        // if we have slots, make an assistant button that will be converted to slot
        // filling
        // the user will still have to click the button to activate the action
        // if we don't have slots, don't make a button just to be clicked, let the
        // action through and wait for sabrina to ask questions

        ControlBinder control = engine.getControl();
        if (control == null)
            return;
        AssistantDispatcher assistant = control.getAssistant();
        assistant.presentExample(item.utterance, item.targetCode);
    }


    private void hideKeyboard(View v) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void showInput() {
        findViewById(R.id.input_bar).setVisibility(View.VISIBLE);
    }

    @Override
    public void onBeforeCommand() {
        mInCommand = true;

        final View view = findViewById(R.id.assistant_progress);
        if (view == null)
            return;

        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mInCommand)
                    view.setVisibility(View.VISIBLE);
            }
        }, 500);
    }

    @Override
    public void onAfterCommand() {
        mInCommand = false;
        findViewById(R.id.assistant_progress).setVisibility(View.GONE);
    }

    void onTextActivated(boolean fromVoice) {
        if (engine != null) {
            EditText input = (EditText) findViewById(R.id.assistant_input);

            String command = input.getText().toString().trim();
            if (command.length() > 0) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;

                AssistantMessage msg;
                if (mIsPassword)
                    msg = control.getAssistant().handlePassword(command);
                else
                    msg = control.getAssistant().handleCommand(command);
                if (msg != null)
                    display(msg);

                input.setText("");
            }
        }
    }

    void showPictureFullscreen(String url) {
        Intent intent = new Intent(this, FullscreenPictureActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private void createDeviceOAuth2(String kind, String name) {
        Intent intent = new Intent(this, OAuthActivity.class);
        intent.setAction(OAuthActivity.ACTION);
        intent.putExtra("extra.KIND", kind);
        intent.putExtra("extra.TITLE", name);
        startActivityForResult(intent, REQUEST_OAUTH2);
    }

    private void showChooseDeviceKindList(Uri url) {
        String _class = url.getQueryParameter("class");
        if (_class == null)
            _class = "physical";

        Intent intent = new Intent(this, DeviceConfigureChooseKindActivity.class);
        intent.setAction(DeviceConfigureChooseKindActivity.ACTION);
        intent.putExtra("extra.CLASS", _class);

        startActivityForResult(intent, REQUEST_CREATE_DEVICE);
    }

    private void showMyGoods() {
        Intent intent = new Intent(this, MyStuffActivity.class);
        startActivity(intent);
    }

    private static Object jsonParse(String what) throws JSONException {
        return new JSONTokener(what).nextValue();
    }

    private void showConfigureDevice(String kind, String name, String controls) {
        try {
            Intent intent = new Intent(this, DeviceCreateFormActivity.class);
            intent.setAction(DeviceCreateFormActivity.ACTION);
            intent.putExtra("extra.KIND", kind);
            intent.putExtra("extra.TITLE", name);
            intent.putExtra("extra.CONTROLS", (Serializable) DeviceFactory.FormControl.fromJSONArray((JSONArray) jsonParse(controls)));
            startActivityForResult(intent, REQUEST_CREATE_DEVICE);
        } catch (JSONException e) {
            Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception configuring " + kind, e);
        }
    }

    void onLinkActivated(String url) {
        if (url.startsWith("/")) {
            // recognize relative urls as local intents
            if (url.startsWith("/devices/oauth2/")) {
                // work around weirdness in Android's query parser
                Uri parsed = Uri.parse("http://127.0.0.1:8080" + url);
                String kind = parsed.getLastPathSegment();
                createDeviceOAuth2(kind, parsed.getQueryParameter("name"));
            } else if (url.startsWith("/devices/configure/")) {
                // work around weirdness in Android's query parser
                Uri parsed = Uri.parse("http://127.0.0.1:8080" + url);
                String kind = parsed.getLastPathSegment();
                showConfigureDevice(kind, parsed.getQueryParameter("name"), parsed.getQueryParameter("controls"));
            } else if (url.startsWith("/devices/create")) {
                showChooseDeviceKindList(Uri.parse(url));
            } else if (url.startsWith("/apps")) {
                showMyGoods();
            }

            // eat all other links
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    void onButtonActivated(String title, JSONObject json) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleButton(title, json));
    }

    void onYesActivated() {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleYes());
    }

    void onNoActivated() {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleNo());
    }

    void onChoiceActivated(String title, int idx) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleChoice(title, idx));
    }

    void onFilterActivated(String title, JSONObject json, String type, String value) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        try {
            if (!value.isEmpty()) {
                title = title.replace("____", value);
                if (type.equals("Number"))
                    json.getJSONObject("filter").put("value", new JSONObject("{value: " + value + "}"));
                else
                    json.getJSONObject("filter").put("value", new JSONObject("{value: \"" + value + "\"}"));
            }
            // FIXME
            display(control.getAssistant().handleButton(title, json));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void onSlotFillingActivated(String title, JSONObject json, JSONArray slots, JSONObject slotTypes, Map<String, String> values) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        try {
            JSONObject entities = json.optJSONObject("entities");
            if (entities == null) {
                entities = new JSONObject();
                json.put("entities", entities);
            }

            for (int i = 0; i < slots.length(); i++) {
                String slotName = slots.getString(i);

                String slotValue = values.get(slotName);
                if (slotValue.length() == 0) {
                    title = title.replace("$" + slotName, "____");
                    continue;
                }

                title = title.replace("$" + slotName, slotValue);
                String slotType = slotTypes.getString(slotName);
                String slotId = "SLOT_" + i;

                entities.put(slotId, getArgValue(slotValue, slotType));
            }
            display(control.getAssistant().handleButton(title, json));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private Object getArgValue(String value, String type) {
        switch (type) {
            case "Number":
                return Double.valueOf(value);
            case "Boolean":
                return value.equals("on");
            default:
                if (type.startsWith("Measure") || type.startsWith("Currency"))
                    return Double.valueOf(value);
                return value;
        }
    }

    private void onLocationSelected(Place place) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleLocation(place));
    }

    private void onPictureSelected(Uri uri) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handlePicture(uri.toString()));
    }


    private class ReadContactTask extends AsyncTask<Void, Void, Pair<String, String>> {
        private final int requestCode;
        private final Uri uri;

        public ReadContactTask(int requestCode, Uri uri) {
            this.requestCode = requestCode;
            this.uri = uri;
        }

        @Override
        protected Pair<String, String> doInBackground(Void... nothing) {
            String[] projection;
            if (requestCode == REQUEST_PHONE_NUMBER)
                projection = new String[]{ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
            else
                projection = new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS, ContactsContract.CommonDataKinds.Email.DISPLAY_NAME};
            try (Cursor cursor = getContentResolver().query(uri, projection,
                    null, null, null)) {
                // If the cursor returned is valid, get the phone number
                if (cursor != null && cursor.moveToFirst())
                    return new Pair<>(cursor.getString(0), cursor.getString(1));
                else
                    return null;
            }
        }

        @Override
        public void onPostExecute(Pair<String, String> result) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return;

            display(control.getAssistant().handleContact(result.first, result.second, requestCode == REQUEST_PHONE_NUMBER ? "tt:phone_number" : "tt:email_address"));
        }
    }

    private void onContactSelected(int requestCode, Uri contact) {
        new ReadContactTask(requestCode, contact).execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        engine.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        engine.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_OAUTH2 || requestCode == REQUEST_CREATE_DEVICE) {
            // do something with it
            // or nothing, probably
            return;
        }

        if (requestCode == REQUEST_ENABLE_PLAY_SERVICES) {
            // not sure about this one, do we try popping up the location picker again?
            return;
        }

        if (requestCode == REQUEST_LOCATION) {
            if (resultCode != Activity.RESULT_OK)
                return;

            onLocationSelected(PlacePicker.getPlace(this, intent));
        }

        if (requestCode == REQUEST_PICTURE) {
            if (resultCode != Activity.RESULT_OK)
                return;

            onPictureSelected(intent.getData());
        }

        if (requestCode == REQUEST_PHONE_NUMBER || requestCode == REQUEST_EMAIL) {
            if (resultCode != Activity.RESULT_OK)
                return;

            onContactSelected(requestCode, intent.getData());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_clear_chat) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            control.getAssistant().handleClear();
            Toast.makeText(getApplicationContext(), "The conversation has been reset.",
                    Toast.LENGTH_LONG).show();
            return true;
        }

        if (id == R.id.action_train) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            control.getAssistant().handleTrain();
            return true;
        }

        if (id == R.id.action_help) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            control.getAssistant().handleHelp();
            return true;
        }

        if (id == R.id.cheatsheet) {
            Intent intent = new Intent(MainActivity.this, CheatsheetActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.my_stuff) {
            Intent intent = new Intent(this, MyStuffActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.my_rules) {
            Intent intent = new Intent(this, MyRulesActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.my_permissions) {
            Intent intent = new Intent(this, MyPermissionsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}



