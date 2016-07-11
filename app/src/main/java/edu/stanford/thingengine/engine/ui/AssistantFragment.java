package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.percent.PercentRelativeLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.microsoft.projectoxford.speechrecognition.ISpeechRecognitionServerEvents;
import com.microsoft.projectoxford.speechrecognition.MicrophoneRecognitionClient;
import com.microsoft.projectoxford.speechrecognition.RecognitionResult;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionMode;
import com.microsoft.projectoxford.speechrecognition.SpeechRecognitionServiceFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantDispatcher;
import edu.stanford.thingengine.engine.service.AssistantMessage;
import edu.stanford.thingengine.engine.service.AssistantOutput;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class AssistantFragment extends Fragment implements AssistantOutput {
    private static final int REQUEST_OAUTH2 = 1;
    private static final int REQUEST_CREATE_DEVICE = 2;

    private boolean mPulledHistory = false;
    private MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            ControlBinder control = mEngine.getControl();
            if (control == null)
                return;

            control.getAssistant().setAssistantOutput(AssistantFragment.this);
            pullHistory(control.getAssistant());
        }
    };
    private FragmentEmbedder mListener;
    private final SpeechHandler mSpeechHandler = new SpeechHandler();

    private class SpeechHandler implements ISpeechRecognitionServerEvents {
        private boolean mMicrophoneOn;
        private FutureTask<MicrophoneRecognitionClient> mMicClientCreateTask;
        private MicrophoneRecognitionClient mMicClient = null;

        public void onCreate() {
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

        }

        public void onPause() {
            if (mMicrophoneOn)
                mMicClient.endMicAndRecognition();
            mMicrophoneOn = false;
        }

        public void startRecording() {
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
            onTextActivated();
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
                display(new AssistantMessage.Text(AssistantMessage.Direction.FROM_SABRINA, "Speak now..."));
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

    public void addItem(@NonNull View view, @NonNull AssistantMessage.Direction side) {
        PercentRelativeLayout.LayoutParams params = new PercentRelativeLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.getPercentLayoutInfo().widthPercent = 0.7f;

        PercentRelativeLayout wrapper = new PercentRelativeLayout(getActivity());

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

        LinearLayout layout = (LinearLayout) getActivity().findViewById(R.id.assistant_container);

        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(wrapper, outerParams);

        scheduleScroll();
    }

    private void scheduleScroll() {
        if (mScrollScheduled)
            return;
        mScrollScheduled = true;

        final ScrollView scrollView = (ScrollView)getActivity().findViewById(R.id.assistant_scroll_view);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollScheduled = false;
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    @Override
    public void display(AssistantMessage msg) {
        switch (msg.type) {
            case TEXT:
                display((AssistantMessage.Text)msg);
                break;
            case PICTURE:
                display((AssistantMessage.Picture)msg);
                break;
            case RDL:
                display((AssistantMessage.RDL)msg);
                break;
            case CHOICE:
                display((AssistantMessage.Choice)msg);
                break;
            case LINK:
                display((AssistantMessage.Link)msg);
                break;
            case BUTTON:
                display((AssistantMessage.Button)msg);
                break;
        }
    }

    private void display(AssistantMessage.Text msg) {
        TextView view = new TextView(getActivity());
        view.setText(msg.msg);
        addItem(view, msg.direction);
    }

    private void display(AssistantMessage.Picture msg) {
        ImageView view = new ImageView(getActivity());
        view.setBackgroundColor(Color.RED);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setAdjustViewBounds(true);
        addItem(view, msg.direction);
        (new LoadImageTask(view) {
            @Override
            public void onPostExecute(Drawable draw) {
                super.onPostExecute(draw);
                scheduleScroll();
            }
        }).execute(msg.url);
    }

    private void display(AssistantMessage.RDL msg) {
        try {
            // FIXME: we can do a better job for RDLs...

            Button btn = new Button(getActivity());
            btn.setText(msg.rdl.optString("displayTitle"));
            final String webCallback = msg.rdl.getString("webCallback");
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onLinkActivated(webCallback);
                }
            });
            addItem(btn, msg.direction);
        } catch(JSONException e) {
            Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception while unpacking RDL", e);
        }
    }

    private void display(final AssistantMessage.Choice msg) {
        Button btn = new Button(getActivity());
        btn.setText(msg.title);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChoiceActivated(msg.idx);
            }
        });
        addItem(btn, msg.direction);
    }

    private void display(final AssistantMessage.Link msg) {
        Button btn = new Button(getActivity());
        btn.setText(msg.title);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLinkActivated(msg.url);
            }
        });
        addItem(btn, msg.direction);
    }

    private void display(final AssistantMessage.Button msg) {
        Button btn = new Button(getActivity());
        btn.setText(msg.title);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonActivated(msg.json);
            }
        });
        addItem(btn, msg.direction);
    }

    private void pullHistory(AssistantDispatcher dispatcher) {
        if (mPulledHistory)
            return;
        mPulledHistory = true;

        for (AssistantMessage msg : dispatcher.getHistory(10))
            display(msg);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        EditText input = (EditText)getActivity().findViewById(R.id.assistant_input);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_NULL) {
                    onTextActivated();
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
        if (control != null)
            control.getAssistant().setAssistantOutput(null);
        mEngine.removeEngineReadyCallback(mReadyCallback);
        mSpeechHandler.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    private void onTextActivated() {
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

    private void createDeviceOAuth2(String kind) {
        Intent intent = new Intent(getActivity(), OAuthActivity.class);
        intent.setAction(OAuthActivity.ACTION);
        intent.putExtra("extra.KIND", kind);
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

    private void onLinkActivated(String url) {
        if (url.startsWith("/")) {
            // recognize relative urls as local intents
            if (url.startsWith("/devices/oauth2/")) {
                Uri parsed = Uri.parse(url);
                String kind = parsed.getLastPathSegment();
                createDeviceOAuth2(kind);
            } else if (url.startsWith("/devices/create")) {
                showChooseDeviceKindList(Uri.parse(url));
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

    private void onChoiceActivated(int idx) {
        if (mEngine != null) {
            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;

                JSONObject obj = new JSONObject();
                JSONObject inner = new JSONObject();
                obj.put("answer", inner);
                inner.put("type", "Choice");
                inner.put("value", idx);

                control.getAssistant().handleParsedCommand(obj.toString());
            } catch(JSONException e) {
                Log.e(MainActivity.LOG_TAG, "Unexpected json exception while constructing choice JSON", e);
            }
        }
    }

    // this version of onAttach is deprecated but it's required
    // on APIs older than 23 because otherwise onAttach is never called
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
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
        mListener = null;
        mEngine = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_OAUTH2 || requestCode == REQUEST_CREATE_DEVICE) {
            // do something with it
        }
    }
}
