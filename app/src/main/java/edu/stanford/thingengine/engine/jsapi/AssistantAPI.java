package edu.stanford.thingengine.engine.jsapi;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import edu.stanford.thingengine.engine.service.AssistantCommandHandler;
import edu.stanford.thingengine.engine.service.AssistantMessage;
import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.service.EngineService;

/**
 * Created by gcampagn on 6/26/16.
 */
public class AssistantAPI extends JavascriptAPI implements AssistantCommandHandler {
    private EngineService mService;

    public AssistantAPI(EngineService service, ControlChannel channel) {
        super("Assistant", channel);

        mService = service;

        registerSync("send", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                send((String)args[0]);
                return null;
            }
        });

        registerSync("sendPicture", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendPicture((String)args[0]);
                return null;
            }
        });

        registerSync("sendRDL", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                try {
                    sendRDL((JSONObject) ((new JSONTokener((String) args[0])).nextValue()));
                } catch (ClassCastException | JSONException e) {
                    Log.e(EngineService.LOG_TAG, "Unexpected exception marshalling sendRDL", e);
                }
                return null;
            }
        });

        registerSync("sendChoice", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendChoice(((Number)args[0]).intValue(), (String)args[1], (String)args[2], (String)args[3]);
                return null;
            }
        });

        registerSync("sendLink", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendLink((String)args[0], (String)args[1]);
                return null;
            }
        });

        registerSync("sendButton", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendButton((String)args[0], (String)args[1]);
                return null;
            }
        });
    }

    @Override
    public void handleCommand(String command) {
        invokeAsync("onhandlecommand", command);
    }

    @Override
    public void handleParsedCommand(String json) {
        invokeAsync("onhandleparsedcommand", json);
    }

    @Override
    public void handlePicture(String url) {
        invokeAsync("onhandlepicture", url);
    }

    private void send(String text) {
        mService.getAssistant().dispatch(new AssistantMessage.Text(text));
    }

    private void sendPicture(String url) {
        mService.getAssistant().dispatch(new AssistantMessage.Picture(url));
    }

    private void sendRDL(JSONObject rdl) {
        mService.getAssistant().dispatch(new AssistantMessage.RDL(rdl));
    }

    private void sendChoice(int idx, String what, String title, String text) {
        mService.getAssistant().dispatch(new AssistantMessage.Choice(idx, title, text));
    }

    private void sendLink(String title, String url) {
        mService.getAssistant().dispatch(new AssistantMessage.Link(title, url));
    }

    private void sendButton(String title, String button) {
        mService.getAssistant().dispatch(new AssistantMessage.Button(title, button));
    }
}
