package edu.stanford.thingengine.engine.jsapi;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import edu.stanford.thingengine.engine.service.EngineService;
import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 6/26/16.
 */
public class AssistantAPI extends JavascriptAPI {
    public AssistantAPI(ControlChannel channel) {
        super("Assistant", channel);

        registerSync("send", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                InteractionCallback callback = getControl().getInteractionCallback();
                if (callback != null)
                    callback.send((String)args[0]);
                return null;
            }
        });

        registerSync("sendPicture", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                InteractionCallback callback = getControl().getInteractionCallback();
                if (callback != null)
                    callback.sendPicture((String)args[0]);
                return null;
            }
        });

        registerSync("sendRDL", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                InteractionCallback callback = getControl().getInteractionCallback();
                if (callback != null) {
                    try {
                        callback.sendRDL((JSONObject) ((new JSONTokener((String) args[0])).nextValue()));
                    } catch (ClassCastException | JSONException e) {
                        Log.e(EngineService.LOG_TAG, "Unexpected exception marshalling sendRDL", e);
                    }
                }
                return null;
            }
        });

        registerSync("sendChoice", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                InteractionCallback callback = getControl().getInteractionCallback();
                if (callback != null)
                    callback.sendChoice(((Number)args[0]).intValue(), (String)args[1], (String)args[2], (String)args[3]);
                return null;
            }
        });

        registerSync("sendLink", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                InteractionCallback callback = getControl().getInteractionCallback();
                if (callback != null)
                    callback.sendLink((String)args[0], (String)args[1]);
                return null;
            }
        });

        registerSync("sendButton", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                InteractionCallback callback = getControl().getInteractionCallback();
                if (callback != null)
                    callback.sendButton((String)args[0], (String)args[1]);
                return null;
            }
        });
    }

    public void handleCommand(String command) {
        invokeAsync("onhandlecommand", command);
    }

    public void handleParsedCommand(String json) {
        invokeAsync("onhandleparsedcommand", json);
    }

    public void handlePicture(String url) {
        invokeAsync("onhandlepicture", url);
    }

    public void assistantReady() {
        invokeAsync("onassistantready", null);
    }
}
