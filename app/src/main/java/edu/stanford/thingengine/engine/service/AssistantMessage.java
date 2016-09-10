package edu.stanford.thingengine.engine.service;

import android.support.annotation.Nullable;

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
        TEXT, PICTURE, RDL, CHOICE, LINK, BUTTON, ASK_SPECIAL, SLOT_FILLING;

        // true if this is some output from sabrina
        public boolean isOutput() {
            return this == TEXT || this == PICTURE;
        }
        // true if this is sabrina prompting the user to click something
        // as a replacement for typing
        public boolean isInteraction() {
            return this == CHOICE || this == BUTTON || this == ASK_SPECIAL || this == SLOT_FILLING;
        }
        // true if this is a link that opens in another window
        public boolean isLink() {
            return this == RDL || this == LINK;
        }
        // true if this is a button or button-like object (Interaction or Link)
        public boolean isButton() {
            return this != TEXT && this != PICTURE && this != RDL;
        }
    };
    public enum AskSpecialType {
        YESNO, LOCATION, PICTURE, PHONE_NUMBER, EMAIL_ADDRESS, NUMBER, DATE, TIME, GENERIC, NULL, UNKNOWN;

        public boolean isChooser() {
            switch (this) {
                case NUMBER:
                case DATE:
                case TIME:
                case GENERIC:
                case NULL:
                case UNKNOWN:
                    return false;
                default:
                    return true;
            }
        }
    }
    public final Direction direction;
    public final Type type;
    @Nullable
    public final String icon;

    protected AssistantMessage(Direction direction, Type type, @Nullable String icon) {
        this.direction = direction;
        this.type = type;
        this.icon = icon;
    }

    public boolean shouldNotify() {
        return true;
    }

    public abstract CharSequence toText();

    public static class Text extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final CharSequence msg;

        public Text(Direction dir, String icon, CharSequence msg) {
            super(dir, Type.TEXT, icon);
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

        public Picture(Direction dir, String icon, String url) {
            super(dir, Type.PICTURE, icon);
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

        public RDL(Direction dir, String icon, JSONObject rdl) {
            super(dir, Type.RDL, icon);
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
            super(dir, Type.CHOICE, null);
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
            super(dir, Type.LINK, null);
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
            super(dir, Type.BUTTON, null);
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
            super(dir, Type.ASK_SPECIAL, null);
            this.what = what;
        }

        @Override
        public boolean shouldNotify() {
            return what.isChooser();
        }

        @Override
        public String toText() {
            return "Sabrina asks for a " + what.toString().toLowerCase();
        }
    }

    public static class SlotFilling extends AssistantMessage {
        private static final long serialVersionUID = 1L;

        public final String title;
        public final String json;

        public SlotFilling(Direction dir, String title, String json) {
            super(dir, Type.SLOT_FILLING, null);
            this.title = title;
            this.json = json;
        }

        @Override
        public String toText() {
            return title;
        }
    }
}
