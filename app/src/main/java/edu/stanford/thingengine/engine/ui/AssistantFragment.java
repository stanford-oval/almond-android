package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.thingengine.engine.R;

public class AssistantFragment extends Fragment implements AssistantOutput {
    private MainServiceConnection mEngine;
    private FragmentEmbedder mListener;

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
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout wrapper = new LinearLayout(getActivity());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams innerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                0.7f);
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, 0, 0.3f);
        Space space = new Space(getActivity());
        if (side == Side.LEFT) {
            innerParams.gravity = Gravity.LEFT;
            spaceParams.gravity = Gravity.RIGHT;
            wrapper.addView(view, innerParams);
            wrapper.addView(space, spaceParams);
            if (view instanceof TextView)
                ((TextView) view).setGravity(Gravity.LEFT);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        } else if (side == Side.RIGHT) {
            innerParams.gravity = Gravity.RIGHT;
            spaceParams.gravity = Gravity.LEFT;
            wrapper.addView(space, spaceParams);
            wrapper.addView(view, innerParams);
            if (view instanceof TextView)
                ((TextView) view).setGravity(Gravity.RIGHT);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        }

        LinearLayout layout = (LinearLayout) getActivity().findViewById(R.id.assistant_container);
        layout.addView(wrapper, outerParams);

        if (!mScrollScheduled)
            scheduleScroll();
    }

    private void scheduleScroll() {
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
        // FIXME STUB!
    }

    @Override
    public void sendRDL(JSONObject rdl) {
        // FIXME STUB!
    }

    @Override
    public void sendChoice(int idx, String what, String title, String text) {
        // FIXME STUB!
    }

    @Override
    public void sendLink(String title, String url) {
        // FIXME STUB!
    }

    @Override
    public void sendButton(String title, String json) {
        // FIXME STUB!
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mEngine != null) {
            mEngine.setAssistantOutput(this);
            mEngine.assistantResume();
        }
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
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mEngine != null)
            mEngine.setAssistantOutput(null);
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
    }
}
