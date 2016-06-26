package edu.stanford.thingengine.engine.ui;

import org.json.JSONObject;

/**
 * Created by gcampagn on 6/26/16.
 */
public interface AssistantOutput {
    void send(String msg);
    void sendPicture(String url);
    void sendRDL(JSONObject rdl);
    void sendChoice(int idx, String what, String title, String text);
    void sendLink(String title, String url);
    void sendButton(String title, String json);
}
