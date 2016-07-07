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
import edu.stanford.thingengine.engine.service.ControlBinder;

public class AssistantFragment extends Fragment implements AssistantOutput {
    private static final int REQUEST_OAUTH2 = 1;
    private static final int REQUEST_CREATE_DEVICE = 2;

    private MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            mEngine.assistantReady();
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
                send("Speak now...");
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

    private enum Side { LEFT, RIGHT };

    public void addItem(@NonNull View view, @NonNull Side side) {
        PercentRelativeLayout.LayoutParams params = new PercentRelativeLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.getPercentLayoutInfo().widthPercent = 0.7f;

        PercentRelativeLayout wrapper = new PercentRelativeLayout(getActivity());

        if (side == Side.LEFT) {
            params.addRule(RelativeLayout.ALIGN_PARENT_START);
            if (view instanceof TextView)
                ((TextView) view).setGravity(Gravity.START);
            wrapper.addView(view, params);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        } else if (side == Side.RIGHT) {
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
    public void send(String msg) {
        TextView view = new TextView(getActivity());
        view.setText(msg);
        addItem(view, Side.LEFT);
    }

    @Override
    public void sendPicture(String url) {
        ImageView view = new ImageView(getActivity());
        view.setBackgroundColor(Color.RED);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setAdjustViewBounds(true);
        addItem(view, Side.LEFT);
        (new LoadImageTask(view) {
            @Override
            public void onPostExecute(Drawable draw) {
                super.onPostExecute(draw);
                scheduleScroll();
            }
        }).execute(url);
    }

    @Override
    public void sendRDL(JSONObject rdl) {
        try {
            Button btn = new Button(getActivity());
            btn.setText(rdl.getString("title"));
            final String webCallback = rdl.getString("webCallback");
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onLinkActivated(webCallback);
                }
            });
            addItem(btn, Side.LEFT);
        } catch(JSONException e) {
            Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception while unpacking RDL", e);
        }
    }

    @Override
    public void sendChoice(final int idx, String what, String title, String text) {
        Button btn = new Button(getActivity());
        btn.setText(title);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChoiceActivated(idx);
            }
        });
        addItem(btn, Side.LEFT);
    }

    @Override
    public void sendLink(String title, final String url) {
        Button btn = new Button(getActivity());
        btn.setText(title);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLinkActivated(url);
            }
        });
        addItem(btn, Side.LEFT);
    }

    @Override
    public void sendButton(String title, final String json) {
        Button btn = new Button(getActivity());
        btn.setText(title);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonActivated(json);
            }
        });
        addItem(btn, Side.LEFT);
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
        mEngine.setAssistantOutput(this);
        mEngine.addEngineReadyCallback(mReadyCallback);
        mEngine.assistantReady();
        mSpeechHandler.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        mEngine.setAssistantOutput(null);
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
                mEngine.handleAssistantCommand(command);

                TextView copy = new TextView(getActivity());
                copy.setText(input.getText());
                addItem(copy, Side.RIGHT);

                input.setText("");
            }
        }
    }

    private void createDeviceNoAuth(String kind) {
        final JSONObject object = new JSONObject();
        try {
            object.put("kind", kind);

            final EngineServiceConnection engine = mEngine;
            if (engine == null)
                return;
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    ControlBinder control = engine.getControl();
                    if (control == null)
                        return;

                    try {
                        control.createDevice(object);
                    } catch (final Exception e) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                DialogUtils.showFailureDialog(getActivity(), "Failed to create device: " + e.getMessage());
                            }
                        });
                    }
                }
            });
        } catch (final Exception e) {
            DialogUtils.showFailureDialog(getActivity(), "Failed to create device: " + e.getMessage());
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
            if (url.startsWith("/devices/create/")) {
                // FIXME this should not be a link, it should be a button

                Uri parsed = Uri.parse(url);
                String kind = parsed.getLastPathSegment();
                createDeviceNoAuth(kind);
            } else if (url.startsWith("/devices/oauth2/")) {
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
        if (mEngine != null) {
            mEngine.handleAssistantParsedCommand(json);
        }
    }

    private void onChoiceActivated(int idx) {
        if (mEngine != null) {
            try {
                JSONObject obj = new JSONObject();
                JSONObject inner = new JSONObject();
                obj.put("answer", inner);
                inner.put("type", "Choice");
                inner.put("value", idx);
                mEngine.handleAssistantParsedCommand(obj.toString());
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
