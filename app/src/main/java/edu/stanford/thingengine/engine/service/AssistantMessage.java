package edu.stanford.thingengine.engine.service;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by gcampagn on 7/10/16.
 */
public abstract class AssistantMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        TEXT, PICTURE, RDL, CHOICE, LINK, BUTTON
    };
    public final Type type;
    protected AssistantMessage(Type type) {
        this.type = type;
    }

    public static class Text extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String msg;

        public Text(String msg) {
            super(Type.TEXT);
            this.msg = msg;
        }
    }

    public static class Picture extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String url;

        public Picture(String url) {
            super(Type.PICTURE);
            this.url = url;
        }
    }

    public static class RDL extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final JSONObject rdl;

        public RDL(JSONObject rdl) {
            super(Type.RDL);
            this.rdl = rdl;
        }
    }

    public static class Choice extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final int idx;
        public final String title;
        public final String text;

        public Choice(int idx, String title, String text) {
            super(Type.CHOICE);
            this.idx = idx;
            this.title = title;
            this.text = text;
        }
    }

    public static class Link extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String title;
        public final String url;

        public Link(String title, String url) {
            super(Type.LINK);
            this.title = title;
            this.url = url;
        }
    }

    public static class Button extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String title;
        public final String json;

        public Button(String title, String json) {
            super(Type.BUTTON);
            this.title = title;
            this.json = json;
        }
    }
}
