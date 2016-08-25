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
                send((String)args[0], (String)args[1]);
                return null;
            }
        });

        registerSync("sendPicture", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendPicture((String)args[0], (String)args[1]);
                return null;
            }
        });

        registerSync("sendRDL", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                try {
                    sendRDL((JSONObject) ((new JSONTokener((String) args[0])).nextValue()), (String)args[1]);
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

        registerSync("sendAskSpecial", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendAskSpecial((String)args[0]);
                return null;
            }
        });
    }

    @Override
    public void ready() {
        invokeAsync("onready", null);
    }

    @Override
    public void handleCommand(String command) {
        invokeAsync("onhandlecommand", command);
    }

    @Override
    public void handleParsedCommand(String json) {
        invokeAsync("onhandleparsedcommand", json);
    }

    private void send(String text, String icon) {
        mService.getAssistant().dispatch(new AssistantMessage.Text(AssistantMessage.Direction.FROM_SABRINA, icon, text));
    }

    private void sendPicture(String url, String icon) {
        mService.getAssistant().dispatch(new AssistantMessage.Picture(AssistantMessage.Direction.FROM_SABRINA, icon, url));
    }

    private void sendRDL(JSONObject rdl, String icon) {
        mService.getAssistant().dispatch(new AssistantMessage.RDL(AssistantMessage.Direction.FROM_SABRINA, icon, rdl));
    }

    private void sendChoice(int idx, String what, String title, String text) {
        mService.getAssistant().dispatch(new AssistantMessage.Choice(AssistantMessage.Direction.FROM_SABRINA, idx, title, text));
    }

    private void sendLink(String title, String url) {
        mService.getAssistant().dispatch(new AssistantMessage.Link(AssistantMessage.Direction.FROM_SABRINA, title, url));
    }

    private void sendButton(String title, String button) {
        mService.getAssistant().dispatch(new AssistantMessage.Button(AssistantMessage.Direction.FROM_SABRINA, title, button));
    }

    private void sendAskSpecial(String what) {
        AssistantMessage.AskSpecialType type;
        if (what == null) {
            type = AssistantMessage.AskSpecialType.NULL;
        } else {
            try {
                type = AssistantMessage.AskSpecialType.valueOf(what.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = AssistantMessage.AskSpecialType.UNKNOWN;
            }
        }
        mService.getAssistant().dispatch(new AssistantMessage.AskSpecial(AssistantMessage.Direction.FROM_SABRINA, type));
    }
}
