package edu.stanford.thingengine.engine.service;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by gcampagn on 7/10/16.
 */
public abstract class AssistantMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Direction {
        FROM_SABRINA, FROM_USER
    }
    public enum Type {
        TEXT, PICTURE, RDL, CHOICE, LINK, BUTTON, ASK_SPECIAL
    };
    public enum AskSpecialType {
        YESNO, LOCATION, PICTURE, PHONE_NUMBER, EMAIL_ADDRESS, ANYTHING, UNKNOWN
    }
    public final Direction direction;
    public final Type type;
    protected AssistantMessage(Direction direction, Type type) {
        this.direction = direction;
        this.type = type;
    }

    public abstract CharSequence toText();

    public static class Text extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final CharSequence msg;

        public Text(Direction dir, CharSequence msg) {
            super(dir, Type.TEXT);
            this.msg = msg;
        }

        @Override
        public CharSequence toText() {
            return msg;
        }
    }

    public static class Picture extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String url;

        public Picture(Direction dir, String url) {
            super(dir, Type.PICTURE);
            this.url = url;
        }

        @Override
        public String toText() {
            return "Picture";
        }
    }

    public static class RDL extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final JSONObject rdl;

        public RDL(Direction dir, JSONObject rdl) {
            super(dir, Type.RDL);
            this.rdl = rdl;
        }

        @Override
        public String toText() {
            try {
                return rdl.getString("displayTitle");
            } catch(JSONException e) {
                return "RDL";
            }
        }
    }

    public static class Choice extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final int idx;
        public final String title;
        public final String text;

        public Choice(Direction dir, int idx, String title, String text) {
            super(dir, Type.CHOICE);
            this.idx = idx;
            this.title = title;
            this.text = text;
        }

        @Override
        public String toText() {
            return title;
        }
    }

    public static class Link extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String title;
        public final String url;

        public Link(Direction dir, String title, String url) {
            super(dir, Type.LINK);
            this.title = title;
            this.url = url;
        }

        @Override
        public String toText() {
            return title;
        }
    }

    public static class Button extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String title;
        public final String json;

        public Button(Direction dir, String title, String json) {
            super(dir, Type.BUTTON);
            this.title = title;
            this.json = json;
        }

        @Override
        public String toText() {
            return title;
        }
    }

    public static class AskSpecial extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final AskSpecialType what;

        public AskSpecial(Direction dir, AskSpecialType what) {
            super(dir, Type.ASK_SPECIAL);
            this.what = what;
        }

        @Override
        public String toText() {
            return "Sabrina asks for a " + what.toString().toLowerCase();
        }
    }
}
