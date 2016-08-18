package edu.stanford.thingengine.engine.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.percent.PercentRelativeLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
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
import org.json.JSONTokener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantDispatcher;
import edu.stanford.thingengine.engine.service.AssistantHistoryModel;
import edu.stanford.thingengine.engine.service.AssistantLifecycleCallbacks;
import edu.stanford.thingengine.engine.service.AssistantMessage;
import edu.stanford.thingengine.engine.service.AssistantOutput;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class AssistantFragment extends Fragment implements AssistantOutput, AssistantLifecycleCallbacks, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int REQUEST_OAUTH2 = 1;
    private static final int REQUEST_CREATE_DEVICE = 2;
    private static final int REQUEST_LOCATION = 3;
    private static final int REQUEST_ENABLE_PLAY_SERVICES = 4;
    private static final int REQUEST_PICTURE = 5;
    private static final int REQUEST_AUDIO_PERMISSION = 6;
    private static final int REQUEST_EMAIL = 7;
    private static final int REQUEST_PHONE_NUMBER = 8;

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
    private AssistantHistoryAdapter mListAdapter = new AssistantHistoryAdapter();
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
                display(new AssistantMessage.Text(AssistantMessage.Direction.FROM_SABRINA, getActivity().getString(R.string.speak_now)));
        }
    }

    private class AssistantHistoryAdapter extends BaseAdapter implements AssistantHistoryModel.Listener {
        private final ArrayList<AssistantMessage> filteredHistory = new ArrayList<>();
        private Context ctx;
        private AssistantHistoryModel history;

        @Override
        public int getCount() {
            return filteredHistory.size();
        }

        @Override
        public AssistantMessage getItem(int position) {
            return filteredHistory.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        private boolean isFiltered(AssistantMessage msg) {
            if (msg.type == AssistantMessage.Type.ASK_SPECIAL) {
                AssistantMessage.AskSpecial askSpecial = (AssistantMessage.AskSpecial)msg;
                if (askSpecial.what == AssistantMessage.AskSpecialType.UNKNOWN)
                    return true;
            }

            return false;
        }

        @Override
        public void onAdded(AssistantMessage msg) {
            if (isFiltered(msg))
                return;

            filteredHistory.add(msg);
            notifyDataSetChanged();
        }

        @Override
        public void onClear() {
            filteredHistory.clear();
            notifyDataSetChanged();
        }

        public void setContext(Context ctx) {
            this.ctx = ctx;
        }

        private Context getContext() {
            return ctx;
        }

        public void setHistory(AssistantHistoryModel history) {
            if (history == this.history)
                return;
            if (this.history != null)
                this.history.removeListener(this);
            this.history = history;
            if (history == null)
                return;
            history.addListener(this);

            filteredHistory.clear();

            for (AssistantMessage msg : history) {
                if (isFiltered(msg))
                    continue;
                filteredHistory.add(msg);
            }
            notifyDataSetChanged();
        }

        private View wrapView(View view, AssistantMessage.Direction side) {
            PercentRelativeLayout.LayoutParams params = new PercentRelativeLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.getPercentLayoutInfo().widthPercent = 0.7f;

            PercentRelativeLayout wrapper = new PercentRelativeLayout(getContext());
            if (view instanceof Button) {
                view.setBackgroundResource(R.drawable.button_sabrina);
                view.setStateListAnimator(null);
                ((Button) view).setTransformationMethod(null);
            } else if (side == AssistantMessage.Direction.FROM_SABRINA)
                view.setBackgroundResource(R.drawable.bubble_sabrina);
            else if (side == AssistantMessage.Direction.FROM_USER)
                view.setBackgroundResource(R.drawable.bubble_user);

            if (side == AssistantMessage.Direction.FROM_SABRINA) {
                params.addRule(RelativeLayout.ALIGN_PARENT_START);
                if (view instanceof TextView)
                    ((TextView) view).setGravity(Gravity.START);
                wrapper.addView(view, params);
                view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            } else if (side == AssistantMessage.Direction.FROM_USER) {
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                if (view instanceof TextView)
                    ((TextView) view).setGravity(Gravity.END);
                wrapper.addView(view, params);
                view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            }

            return wrapper;
        }

        private View display(AssistantMessage msg) {
            switch (msg.type) {
                case TEXT:
                    return display((AssistantMessage.Text) msg);
                case PICTURE:
                    return display((AssistantMessage.Picture) msg);
                case RDL:
                    return display((AssistantMessage.RDL) msg);
                case CHOICE:
                    return display((AssistantMessage.Choice) msg);
                case LINK:
                    return display((AssistantMessage.Link) msg);
                case BUTTON:
                    return display((AssistantMessage.Button) msg);
                case ASK_SPECIAL:
                    return display((AssistantMessage.AskSpecial) msg);
                default:
                    throw new RuntimeException();
            }
        }

        private Button makeContactPickerButton(final int requestCode) {
            Button btn = new Button(getContext());
            btn.setText(R.string.btn_choose_contact);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showContactPicker(requestCode);
                }
            });
            return btn;
        }

        private LinearLayout makeYesNoButtons() {
            Context mContext = getContext();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            LinearLayout yesno = new LinearLayout(mContext);
            yesno.setOrientation(LinearLayout.HORIZONTAL);
            yesno.setLayoutParams(params);
            Button yesbtn = new Button(mContext);
            Button nobtn = new Button(mContext);
            yesbtn.setLayoutParams(params);
            yesbtn.setText(R.string.yes);
            yesbtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onYesActivated();
                }
            });
            nobtn.setLayoutParams(params);
            nobtn.setText(R.string.no);
            nobtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onNoActivated();
                }
            });
            yesno.addView(yesbtn);
            yesno.addView(nobtn);
            return yesno;
        }

        private View display(AssistantMessage.AskSpecial msg) {
            Button btn;

            switch (msg.what) {
                case YESNO:
                    return wrapView(makeYesNoButtons(), msg.direction);

                case LOCATION:
                    btn = new Button(getContext());
                    btn.setText(R.string.btn_choose_location);
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showLocationPicker();
                        }
                    });
                    return wrapView(btn, msg.direction);

                case PICTURE:
                    btn = new Button(getContext());
                    btn.setText(R.string.btn_choose_picture);
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showImagePicker();
                        }
                    });
                    return wrapView(btn, msg.direction);

                case PHONE_NUMBER:
                case EMAIL_ADDRESS:
                    return wrapView(makeContactPickerButton(msg.what == AssistantMessage.AskSpecialType.PHONE_NUMBER ? REQUEST_PHONE_NUMBER : REQUEST_EMAIL),
                            msg.direction);
                case UNKNOWN:
                case ANYTHING:
                    // we don't recognize this, it should have been filtered by isFiltered()
                    throw new RuntimeException();

                default:
                    throw new RuntimeException();
            }
        }

        private View display(AssistantMessage.Text msg) {
            TextView view = new TextView(getContext());
            view.setText(msg.msg);
            return wrapView(view, msg.direction);
        }

        private View display(AssistantMessage.Picture msg) {
            ImageView view = new ImageView(getContext());
            view.setBackgroundColor(Color.RED);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setAdjustViewBounds(true);
            (new LoadImageTask(getContext(), view) {
                @Override
                public void onPostExecute(Drawable draw) {
                    super.onPostExecute(draw);
                    scheduleScroll();
                }
            }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, msg.url);
            return wrapView(view, msg.direction);
        }

        private View display(AssistantMessage.RDL msg) {
            try {
                // FIXME: we can do a better job for RDLs...

                Button btn = new Button(getContext());
                btn.setText(msg.rdl.optString("displayTitle"));
                String webCallback = msg.rdl.getString("webCallback");
                final String url;
                if (webCallback.startsWith("http"))
                    url = webCallback;
                else
                    url = "http://" + webCallback;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onLinkActivated(url);
                    }
                });
                return wrapView(btn, msg.direction);
            } catch (JSONException e) {
                Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception while unpacking RDL", e);
                return null;
            }
        }

        private View display(final AssistantMessage.Choice msg) {
            Button btn = new Button(getContext());
            btn.setText(msg.title);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onChoiceActivated(msg.idx);
                }
            });
            return wrapView(btn, msg.direction);
        }

        private View display(final AssistantMessage.Link msg) {
            Button btn = new Button(getContext());
            btn.setText(msg.title);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onLinkActivated(msg.url);
                }
            });
            return wrapView(btn, msg.direction);
        }

        private View display(final AssistantMessage.Button msg) {
            Button btn = new Button(getContext());
            btn.setText(msg.title);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onButtonActivated(msg.json);
                }
            });
            return wrapView(btn, msg.direction);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return display(getItem(position));
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

    public void display(AssistantMessage msg) {
        if (msg.direction == AssistantMessage.Direction.FROM_SABRINA &&
                msg.type == AssistantMessage.Type.TEXT)
            mSpeechHandler.say(msg.toText());

        scheduleScroll();
    }

    private void scheduleScroll() {
        if (mScrollScheduled)
            return;
        mScrollScheduled = true;

        final ListView listView = (ListView)getActivity().findViewById(R.id.chat_list);
        listView.post(new Runnable() {
            @Override
            public void run() {
                mScrollScheduled = false;
                listView.smoothScrollToPosition(mListAdapter.getCount()-1);
            }
        });
    }

    private void showLocationPicker() {
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

    private void showImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_PICTURE);
    }

    private void showContactPicker(int requestCode) {
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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        EditText input = (EditText)getActivity().findViewById(R.id.assistant_input);
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

        View voicebtn = getActivity().findViewById(R.id.btn_assistant_voice);
        voicebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpeechHandler.startRecording();
            }
        });

        ((ListView)getActivity().findViewById(R.id.chat_list)).setAdapter(mListAdapter);

        getActivity().findViewById(R.id.assistant_progress).setVisibility(View.GONE);

        mSpeechHandler.onCreate();
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

    private void onTextActivated(boolean fromVoice) {
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

    private void onLinkActivated(String url) {
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

    private void onButtonActivated(String json) {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        control.getAssistant().handleParsedCommand(json);
    }

    private void onYesActivated() {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        control.getAssistant().handleYes();
    }

    private void onNoActivated() {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        control.getAssistant().handleNo();
    }

    private void onChoiceActivated(int idx) {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;

        control.getAssistant().handleChoice(idx);
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
