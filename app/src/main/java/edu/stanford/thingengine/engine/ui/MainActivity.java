package edu.stanford.thingengine.engine.ui;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.microsoft.projectoxford.speechrecognition.ISpeechRecognitionServerEvents;
import com.microsoft.projectoxford.speechrecognition.MicrophoneRecognitionClient;
import com.microsoft.projectoxford.speechrecognition.RecognitionResult;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionMode;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionServiceFactory;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;
import net.hockeyapp.android.metrics.MetricsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.Config;
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
    private final SpeechHandler mSpeechHandler = new SpeechHandler();
    private boolean mInCommand = false;

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
        final AutoCompleteTextView input = (AutoCompleteTextView)findViewById(R.id.assistant_input);
        input.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                hideSuggestionBar();
                return false;
            }
        });
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
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setThreshold(1);
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

        View voicebtn = findViewById(R.id.btn_assistant_voice);
        voicebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpeechHandler.startRecording();
            }
        });

        final RecyclerView chatList = ((RecyclerView) findViewById(R.id.chat_list));

        chatList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideKeyboard(v);
                showInput();
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showSuggestionBar();
                    }
                }, 100);
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
        setupSuggestionBar();

        mSpeechHandler.onCreate();

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
        mSpeechHandler.onResume();
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
        mSpeechHandler.onPause();
        engine.stop(this);
        UpdateManager.unregister();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mListAdapter.setHistory(null);
        mListAdapter.setContext(null);
    }

    private class SpeechHandler implements ISpeechRecognitionServerEvents, TextToSpeech.OnInitListener {
        private boolean mMicrophoneOn;
        private FutureTask<MicrophoneRecognitionClient> mMicClientCreateTask;
        private MicrophoneRecognitionClient mMicClient = null;
        private boolean mIsSpeechMode;
        private TextToSpeech mtts = null;
        private boolean mttsInitialized = false;
        private final Queue<CharSequence> mOutputQueue = new LinkedList<>();

        public void onCreate() {
            mIsSpeechMode = false;
            mMicrophoneOn = false;
            mMicClientCreateTask = new FutureTask<>(new Callable<MicrophoneRecognitionClient>() {
                @Override
                public MicrophoneRecognitionClient call() throws Exception {
                    return SpeechRecognitionServiceFactory.createMicrophoneClient(MainActivity.this,
                            SpeechRecognitionMode.ShortPhrase,
                            Config.getLanguage(),
                            SpeechHandler.this,
                            Config.MS_SPEECH_RECOGNITION_PRIMARY_KEY,
                            Config.MS_SPEECH_RECOGNITION_SECONDARY_KEY);
                }
            });
            AsyncTask.THREAD_POOL_EXECUTOR.execute(mMicClientCreateTask);
        }

        public void onResume() {
            if (mIsSpeechMode && mtts == null) {
                mtts = new TextToSpeech(MainActivity.this, this);
                mttsInitialized = false;
            }
        }

        public void onPause() {
            if (mMicrophoneOn)
                mMicClient.endMicAndRecognition();
            mMicrophoneOn = false;

            if (mtts != null) {
                mtts.shutdown();
                mtts = null;
                mttsInitialized = false;
            }
        }

        public void onInit(int success) {
            if (success == TextToSpeech.ERROR)
                return;

            mttsInitialized = true;
            CharSequence seq;
            while ((seq = mOutputQueue.poll()) != null) {
                mtts.speak(seq, TextToSpeech.QUEUE_ADD, null, null);
            }
        }

        public void say(CharSequence what) {
            if (!mIsSpeechMode)
                return;

            if (mttsInitialized)
                mtts.speak(what, TextToSpeech.QUEUE_ADD, null, null);
            else
                mOutputQueue.offer(what);
        }

        public void startRecording() {
            mIsSpeechMode = true;
            if (mtts == null) {
                mtts = new TextToSpeech(MainActivity.this, this);
                mttsInitialized = false;
            }

            int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED)
                startRecordingWithPermission();
            else
                requestPermission();
        }

        public void switchToTextMode() {
            mIsSpeechMode = false;
        }

        private void requestPermission() {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.RECORD_AUDIO))
                Toast.makeText(MainActivity.this, R.string.audio_permission_needed, Toast.LENGTH_LONG).show();

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
        }

        private void startRecordingWithPermission() {
            if (mMicClient == null) {
                try {
                    mMicClient = mMicClientCreateTask.get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(MainActivity.LOG_TAG, "Failed to create microphone client", e);
                    return;
                }
            }
            if (mMicrophoneOn)
                return;

            mMicrophoneOn = true;
            mMicClient.startMicAndRecognition();
        }

        @Override
        public void onPartialResponseReceived(String text) {
            if (!mMicrophoneOn)
                return;
            EditText editor = (EditText) findViewById(R.id.assistant_input);
            editor.setText(text);
        }

        @Override
        public void onFinalResponseReceived(RecognitionResult recognitionResult) {
            mMicClient.endMicAndRecognition();
            mMicrophoneOn = false;
            onTextActivated(true);
        }

        @Override
        public void onIntentReceived(String intent) {
        }

        @Override
        public void onError(int code, String message) {
            Log.w(MainActivity.LOG_TAG, "Error reported by speech recognition server: " + message);
        }

        @Override
        public void onAudioEvent(boolean recording) {
            if (recording)
                Toast.makeText(MainActivity.this, R.string.speak_now, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean mScrollScheduled;

    private void syncSuggestions(AssistantMessage.AskSpecial msg) {
        final boolean visible = msg.what != AssistantMessage.AskSpecialType.NULL;
        findViewById(R.id.suggestion_nevermind).setVisibility(visible ? View.VISIBLE : View.GONE);
        findViewById(R.id.suggestion_twitter).setVisibility(visible ? View.GONE : View.VISIBLE);
        findViewById(R.id.suggestion_gmail).setVisibility(visible ? View.GONE : View.VISIBLE);
        findViewById(R.id.suggestion_nest).setVisibility(visible ? View.GONE : View.VISIBLE);
        findViewById(R.id.suggestion_news).setVisibility(visible ? View.GONE : View.VISIBLE);
        findViewById(R.id.suggestion_discover).setVisibility(visible ? View.GONE : View.VISIBLE);
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
            default:
                type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                break;
        }
        ((TextView) findViewById(R.id.assistant_input)).setInputType(type);
    }

    public void display(AssistantMessage msg) {
        if (msg.direction == AssistantMessage.Direction.FROM_SABRINA &&
                msg.type == AssistantMessage.Type.TEXT)
            mSpeechHandler.say(msg.toText());
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL) {
            syncSuggestions((AssistantMessage.AskSpecial) msg);
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
                    listView.smoothScrollToPosition(mListAdapter.getItemCount()-1);
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

        final ControlBinder control = engine.getControl();
        if (control == null)
            return;
        control.getAssistant().collapseButtons();

        if (item.targetJson.contains("\"slots\":[\"") && !item.targetJson.contains("\"slots\":[]")) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        control.presentSlotFilling(item.utterance, item.targetJson);
                    } catch(Exception e) {
                        Log.e(MainActivity.LOG_TAG, "Failed to prepare slot filling button", e);
                        // fall back to slot filling questions
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onButtonActivated(item.utterance, item.targetJson);
                            }
                        });
                    }
                }
            });

        } else {
            onButtonActivated(item.utterance, item.targetJson);
        }
    }

    private void setupSuggestionBar() {
        View suggestion_help = findViewById(R.id.suggestion_help);
        suggestion_help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp();
            }
        });
        View suggestion_nevermind = findViewById(R.id.suggestion_nevermind);
        suggestion_nevermind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleNeverMind();
            }
        });
        View suggestion_twitter = findViewById(R.id.suggestion_twitter);
        suggestion_twitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp("tt:device.twitter");
            }
        });
        View suggestion_gmail = findViewById(R.id.suggestion_gmail);
        suggestion_gmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp("tt:device.gmail");
            }
        });
        View suggestion_nest = findViewById(R.id.suggestion_nest);
        suggestion_nest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp("tt:device.nest");
            }
        });
        View suggestion_news = findViewById(R.id.suggestion_news);
        suggestion_news.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp("tt:device.washington_post");
            }
        });
        View suggestion_discover = findViewById(R.id.suggestion_discover);
        suggestion_discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleDiscover();
            }
        });
    }

    private void hideKeyboard(View v) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void showInput() {
        findViewById(R.id.input_bar).setVisibility(View.VISIBLE);
    }

    private void hideSuggestionBar() {
        findViewById(R.id.suggestion_bar).setVisibility(View.GONE);
    }

    private void showSuggestionBar() {
        findViewById(R.id.suggestion_bar).setVisibility(View.VISIBLE);
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
        if (!fromVoice)
            mSpeechHandler.switchToTextMode();

        if (engine != null) {
            EditText input = (EditText) findViewById(R.id.assistant_input);

            String command = input.getText().toString().trim();
            if (command.length() > 0) {
                ControlBinder control = engine.getControl();
                if (control == null)
                    return;

                AssistantMessage msg = control.getAssistant().handleCommand(command);
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
        } catch(JSONException e) {
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

    void onButtonActivated(String title, String json) {
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

    void onSlotFillingActivated(String title, String json, JSONObject slotTypes, Map<String, String> values) {
        ControlBinder control = engine.getControl();
        if (control == null)
            return;

        try {
            JSONObject jsonObj = new JSONObject(json);
            String cmdType = jsonObj.keys().next();
            JSONObject cmd = jsonObj.getJSONObject(cmdType);
            if (!cmd.has("slots") || cmd.getJSONArray("slots").length() == 0)
                display(control.getAssistant().handleButton(title, json));
            else {
                JSONArray slots = cmd.getJSONArray("slots");
                JSONArray args = new JSONArray();

                for (int i = slots.length() - 1; i >= 0; i--) {
                    String slotName = slots.getString(i);
                    String slotValue = values.get(slotName);
                    if (slotValue.length() == 0) {
                        title = title.replace("$" + slotName, "____");
                        continue;
                    }

                    String slotType = slotTypes.getString(slotName);

                    JSONObject argJson = new JSONObject();
                    // set argument name
                    JSONObject argName = new JSONObject();
                    argName.put("id", "tt:param." + slotName);
                    argJson.put("name", argName);
                    // set argument type
                    argJson.put("type", getArgType(slotType));
                    // set argument value
                    JSONObject argValue = new JSONObject();
                    argValue.put("value", getArgValue(slotValue, slotType));
                    argJson.put("value", argValue);
                    // set operator
                    argJson.put("operator", "is");
                    // add argument
                    args.put(argJson);
                    // replace slot (underscore) by the argument value
                    title = title.replace("$" + slotName, slotValue.trim());
                }
                cmd.put("args", args);
                display(control.getAssistant().handleButton(title, jsonObj.toString()));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private Object getArgValue(String value, String type) {
        switch(type) {
            case "Number":
                return Integer.valueOf(value);
            case "Boolean":
                if (value.equals("on"))
                    return true;
                else
                    return false;
            default:
                return value;
        }
    }

    private String getArgType(String type) {
        if (type.startsWith("Enum("))
            return "Enum";
        else if (type.equals("Boolean"))
            return "Bool";
        else
            return type;
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

            display(control.getAssistant().handleContact(result.first, result.second, requestCode == REQUEST_PHONE_NUMBER ? "PhoneNumber" : "EmailAddress"));
        }
    }

    private void onContactSelected(int requestCode, Uri contact) {
        new ReadContactTask(requestCode, contact).execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Map<String, Integer> permissionMap = new ArrayMap<>();
        for (int i = 0; i < permissions.length; i++)
            permissionMap.put(permissions[i], grantResults[i]);

        if (permissionMap.containsKey(Manifest.permission.RECORD_AUDIO) &&
                permissionMap.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            mSpeechHandler.startRecording();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

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

        if (id == R.id.my_stuff) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            Intent intent = new Intent(this, MyStuffActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.my_rules) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            Intent intent = new Intent(this, MyRulesActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}



