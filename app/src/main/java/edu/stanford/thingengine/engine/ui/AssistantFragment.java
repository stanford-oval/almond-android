package edu.stanford.thingengine.engine.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.microsoft.projectoxford.speechrecognition.ISpeechRecognitionServerEvents;
import com.microsoft.projectoxford.speechrecognition.MicrophoneRecognitionClient;
import com.microsoft.projectoxford.speechrecognition.RecognitionResult;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionMode;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionServiceFactory;

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

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantDispatcher;
import edu.stanford.thingengine.engine.service.AssistantLifecycleCallbacks;
import edu.stanford.thingengine.engine.service.AssistantMessage;
import edu.stanford.thingengine.engine.service.AssistantOutput;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class AssistantFragment extends Fragment implements AssistantOutput, AssistantLifecycleCallbacks, ActivityCompat.OnRequestPermissionsResultCallback {
    static final int REQUEST_OAUTH2 = 1;
    static final int REQUEST_CREATE_DEVICE = 2;
    static final int REQUEST_LOCATION = 3;
    static final int REQUEST_ENABLE_PLAY_SERVICES = 4;
    static final int REQUEST_PICTURE = 5;
    static final int REQUEST_AUDIO_PERMISSION = 6;
    static final int REQUEST_EMAIL = 7;
    static final int REQUEST_PHONE_NUMBER = 8;

    private MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            ControlBinder control = mEngine.getControl();
            if (control == null)
                return;

            AssistantDispatcher assistant = control.getAssistant();
            assistant.setAssistantOutput(AssistantFragment.this);
            assistant.setAssistantCallbacks(AssistantFragment.this);
            mListAdapter.setHistory(assistant.getHistory());
            assistant.ready();
        }
    };
    private FragmentEmbedder mListener;
    private AssistantHistoryAdapter mListAdapter = new AssistantHistoryAdapter(this);
    private final SpeechHandler mSpeechHandler = new SpeechHandler();
    private boolean mInCommand = false;

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
                    return SpeechRecognitionServiceFactory.createMicrophoneClient(getActivity(),
                            SpeechRecognitionMode.ShortPhrase,
                            Config.LOCALE,
                            SpeechHandler.this,
                            Config.MS_SPEECH_RECOGNITION_PRIMARY_KEY,
                            Config.MS_SPEECH_RECOGNITION_SECONDARY_KEY);
                }
            });
            AsyncTask.THREAD_POOL_EXECUTOR.execute(mMicClientCreateTask);
        }

        public void onResume() {
            if (mIsSpeechMode && mtts == null) {
                mtts = new TextToSpeech(getActivity(), this);
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
                mtts = new TextToSpeech(getActivity(), this);
                mttsInitialized = false;
            }

            int permissionCheck = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED)
                startRecordingWithPermission();
            else
                requestPermission();
        }

        public void switchToTextMode() {
            mIsSpeechMode = false;
        }

        private void requestPermission() {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO))
                Toast.makeText(getActivity(), R.string.audio_permission_needed, Toast.LENGTH_LONG).show();

            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
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
            EditText editor = (EditText) getActivity().findViewById(R.id.assistant_input);
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
                Toast.makeText(getActivity(), R.string.speak_now, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean mScrollScheduled;

    public AssistantFragment() {
        // Required empty public constructor
    }

    public static AssistantFragment newInstance() {
        AssistantFragment fragment = new AssistantFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private void syncNeverMindButton(AssistantMessage.AskSpecial msg) {
        final boolean visible = msg.what != AssistantMessage.AskSpecialType.NULL;
        getActivity().findViewById(R.id.btn_never_mind).setVisibility(visible ? View.VISIBLE : View.GONE);
        getActivity().findViewById(R.id.suggestion_nevermind).setVisibility(visible ? View.VISIBLE : View.GONE);
        getActivity().findViewById(R.id.suggestion_others).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                if (visible)
                    control.getAssistant().handleNeverMind();
                control.getAssistant().handleHelp();
            }
        });
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
        ((TextView)getActivity().findViewById(R.id.assistant_input)).setInputType(type);
    }

    public void display(AssistantMessage msg) {
        if (msg.direction == AssistantMessage.Direction.FROM_SABRINA &&
                msg.type == AssistantMessage.Type.TEXT)
            mSpeechHandler.say(msg.toText());
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL) {
            syncNeverMindButton((AssistantMessage.AskSpecial) msg);
            syncKeyboardType((AssistantMessage.AskSpecial) msg);
        }

        scheduleScroll();
    }

    void scheduleScroll() {
        if (mScrollScheduled)
            return;
        mScrollScheduled = true;

        final RecyclerView listView = (RecyclerView)getActivity().findViewById(R.id.chat_list);
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
            Intent pickerIntent = new PlacePicker.IntentBuilder().build(getActivity());
            startActivityForResult(pickerIntent, REQUEST_LOCATION);
        } catch (GooglePlayServicesNotAvailableException e) {
            DialogUtils.showAlertDialog(getActivity(), "Google Play Services is not available", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        } catch (GooglePlayServicesRepairableException e) {
            GoogleApiAvailability.getInstance().getErrorDialog(getActivity(),
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mListAdapter.setHistory(null);
    }

    private void onAutoCompletionClicked(final AutoCompletionAdapter.Item item) {
        // if we have slots, make an assistant button that will be converted to slot
        // filling
        // the user will still have to click the button to activate the action
        // if we don't have slots, don't make a button just to be clicked, let the
        // action through and wait for sabrina to ask questions
        if (item.targetJson.contains("\"slots\":[\"") && !item.targetJson.contains("\"slots\":[]")) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    ControlBinder control = mEngine.getControl();
                    if (control == null)
                        return;
                    try {
                        control.presentSlotFilling(item.utterance, item.targetJson);
                    } catch(Exception e) {
                        Log.e(MainActivity.LOG_TAG, "Failed to prepare slot filling button", e);
                        // fall back to slot filling questions
                        getActivity().runOnUiThread(new Runnable() {
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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final AutoCompleteTextView input = (AutoCompleteTextView)getActivity().findViewById(R.id.assistant_input);
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
        input.setAdapter(new AutoCompletionAdapter(new ThingpediaClient(getActivity()), getActivity()));
        input.setThreshold(4);
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

        View voicebtn = getActivity().findViewById(R.id.btn_assistant_voice);
        voicebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpeechHandler.startRecording();
            }
        });

        final RecyclerView chatList = ((RecyclerView)getActivity().findViewById(R.id.chat_list));

        chatList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideKeyboard(v);
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

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setStackFromEnd(false);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.setSmoothScrollbarEnabled(false);
        chatList.setLayoutManager(layoutManager);

        getActivity().findViewById(R.id.assistant_progress).setVisibility(View.GONE);

        View nevermind = getActivity().findViewById(R.id.btn_never_mind);
        nevermind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;

                display(control.getAssistant().handleNeverMind());
            }
        });
        nevermind.setVisibility(View.GONE);

        View suggestion_help = getActivity().findViewById(R.id.suggestion_help);
        suggestion_help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp();
            }
        });
        View suggestion_nevermind = getActivity().findViewById(R.id.suggestion_nevermind);
        suggestion_nevermind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleNeverMind();
            }
        });
        View suggestion_twitter = getActivity().findViewById(R.id.suggestion_twitter);
        suggestion_twitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                display(control.getAssistant().handleCommand("help twitter"));
            }
        });
        View suggestion_gmail = getActivity().findViewById(R.id.suggestion_gmail);
        suggestion_gmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                display(control.getAssistant().handleCommand("help gmail"));
            }
        });
        View suggestion_nest = getActivity().findViewById(R.id.suggestion_nest);
        suggestion_nest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                display(control.getAssistant().handleCommand("help nest"));
            }
        });
        View suggestion_wp = getActivity().findViewById(R.id.suggestion_wp);
        suggestion_wp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                display(control.getAssistant().handleCommand("help washington post"));
            }
        });
        View suggestion_others = getActivity().findViewById(R.id.suggestion_others);
        suggestion_others.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;
                control.getAssistant().handleHelp();
            }
        });

        mSpeechHandler.onCreate();
    }

    private void hideKeyboard(View v) {
        InputMethodManager keyboard = (InputMethodManager) this.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        mEngine.addEngineReadyCallback(mReadyCallback);
        mReadyCallback.run();
        mSpeechHandler.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        ControlBinder control = mEngine.getControl();
        if (control != null) {
            AssistantDispatcher assistant = control.getAssistant();
            assistant.setAssistantOutput(null);
            assistant.setAssistantCallbacks(null);
        }
        mEngine.removeEngineReadyCallback(mReadyCallback);
        mSpeechHandler.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onBeforeCommand() {
        mInCommand = true;

        final View view = getView();
        if (view == null)
            return;

        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mInCommand)
                    view.findViewById(R.id.assistant_progress).setVisibility(View.VISIBLE);
            }
        }, 500);
    }

    @Override
    public void onAfterCommand() {
        mInCommand = false;

        final View view = getView();
        if (view == null)
            return;
        view.findViewById(R.id.assistant_progress).setVisibility(View.GONE);
    }

    void onTextActivated(boolean fromVoice) {
        if (!fromVoice)
            mSpeechHandler.switchToTextMode();

        if (mEngine != null) {
            EditText input = (EditText)getActivity().findViewById(R.id.assistant_input);

            String command = input.getText().toString().trim();
            if (command.length() > 0) {
                ControlBinder control = mEngine.getControl();
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
        Intent intent = new Intent(getActivity(), FullscreenPictureActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private void createDeviceOAuth2(String kind, String name) {
        Intent intent = new Intent(getActivity(), OAuthActivity.class);
        intent.setAction(OAuthActivity.ACTION);
        intent.putExtra("extra.KIND", kind);
        intent.putExtra("extra.TITLE", name);
        startActivityForResult(intent, REQUEST_OAUTH2);
    }

    private void showChooseDeviceKindList(Uri url) {
        String _class = url.getQueryParameter("class");
        if (_class == null)
            _class = "physical";

        Intent intent = new Intent(getActivity(), DeviceConfigureChooseKindActivity.class);
        intent.setAction(DeviceConfigureChooseKindActivity.ACTION);
        intent.putExtra("extra.CLASS", _class);

        startActivityForResult(intent, REQUEST_CREATE_DEVICE);
    }

    private void showMyGoods() {
        mListener.switchToMyGoods();
    }

    private static Object jsonParse(String what) throws JSONException {
        return new JSONTokener(what).nextValue();
    }

    private void showConfigureDevice(String kind, String name, String controls) {
        try {
            Intent intent = new Intent(getActivity(), DeviceCreateFormActivity.class);
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
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleButton(title, json));
    }

    void onYesActivated() {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleYes());
    }

    void onNoActivated() {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleNo());
    }

    void onChoiceActivated(String title, int idx) {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleChoice(title, idx));
    }

    void onSlotFillingActivated(String title, String json, JSONObject slotTypes, Map<String, String> values) {
        ControlBinder control = mEngine.getControl();
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
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        display(control.getAssistant().handleLocation(place));
    }

    private void onPictureSelected(Uri uri) {
        ControlBinder control = mEngine.getControl();
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
            try (Cursor cursor = getActivity().getContentResolver().query(uri, projection,
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
            ControlBinder control = mEngine.getControl();
            if (control == null)
                return;

            display(control.getAssistant().handleContact(result.first, result.second, requestCode == REQUEST_PHONE_NUMBER ? "PhoneNumber" : "EmailAddress"));
        }
    }

    private void onContactSelected(int requestCode, Uri contact) {
        new ReadContactTask(requestCode, contact).execute();
    }

    // this version of onAttach is deprecated but it's required
    // on APIs older than 23 because otherwise onAttach is never called
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListAdapter.setContext(activity);
        if (activity instanceof FragmentEmbedder) {
            mListener = (FragmentEmbedder) activity;
            mEngine = mListener.getEngine();
        } else {
            throw new RuntimeException(activity.toString()
                    + " must implement AssistantFragmentEmbedder");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListAdapter.setContext(null);
        mListener = null;
        mEngine = null;
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

            onLocationSelected(PlacePicker.getPlace(getActivity(), intent));
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
}
