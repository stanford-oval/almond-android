// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.jsapi;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.stanford.thingengine.engine.service.AssistantCommandHandler;
import edu.stanford.thingengine.engine.service.AssistantMessage;
import edu.stanford.thingengine.engine.service.EngineService;

/**
 * Created by gcampagn on 6/26/16.
 */
public class AssistantAPI extends JavascriptAPI implements AssistantCommandHandler {
    private EngineService mService;

    public AssistantAPI(EngineService service) {
        super("Assistant");

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
                sendRDL((JSONObject) args[0], (String)args[1]);
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
                sendButton((String)args[0], (JSONObject) args[1]);
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
    public void handleParsedCommand(JSONObject json) {
        invokeAsync("onhandleparsedcommand", json);
    }

    @Override
    public void handleThingTalk(String code) {
        invokeAsync("onhandlethingtalk", code);
    }

    private JSONArray arrayToJSONArray(Object... what) {
        JSONArray arr = new JSONArray();
        for (Object o : what)
            arr.put(o);
        return arr;
    }

    @Override
    public void presentExample(String utterance, String targetCode) {
        invokeAsync("onpresentexample", arrayToJSONArray(utterance, targetCode));
    }

    private void send(String text, String icon) {
        mService.getAssistant().dispatch(new AssistantMessage.Text(AssistantMessage.Direction.FROM_SABRINA, icon, text));
    }

    private void sendPicture(String url, String icon) {
        mService.getAssistant().dispatch(new AssistantMessage.Picture(AssistantMessage.Direction.FROM_SABRINA, icon, url));
    }

    private void sendRDL(JSONObject rdl, String icon) {
        mService.getAssistant().dispatch(new AssistantMessage.RDL(AssistantMessage.Direction.FROM_SABRINA, icon, rdl));
        if (rdl.has("pictureUrl"))
          mService.getAssistant().dispatch(new AssistantMessage.Picture(AssistantMessage.Direction.FROM_SABRINA, icon, rdl.getString("pictureUrl")));
    }

    private void sendChoice(int idx, String what, String title, String text) {
        mService.getAssistant().dispatch(new AssistantMessage.Choice(AssistantMessage.Direction.FROM_SABRINA, idx, title, text));
    }

    private void sendLink(String title, String url) {
        mService.getAssistant().dispatch(new AssistantMessage.Link(AssistantMessage.Direction.FROM_SABRINA, title, url));
    }

    private void sendButton(String title, JSONObject button) {
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
