package edu.stanford.thingengine.engine.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.percent.PercentRelativeLayout;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.koushikdutta.ion.Ion;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantHistoryModel;
import edu.stanford.thingengine.engine.service.AssistantMessage;

/**
 * Created by gcampagn on 8/22/16.
 */
class AssistantHistoryAdapter extends RecyclerView.Adapter<AssistantHistoryAdapter.AssistantMessageViewHolder> implements AssistantHistoryModel.Listener {
    private MainActivity fragment;

    public abstract static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        protected final Context ctx;
        private ImageView sabrinaHead = null;
        private AssistantMessage.Direction cachedSide = null;

        private static Drawable sabrinaHeadBubble;

        private static Drawable getSabrinaHeadBubble(Context ctx) {
            if (sabrinaHeadBubble != null)
                return sabrinaHeadBubble;
            return sabrinaHeadBubble = ctx.getDrawable(R.drawable.sabrina_head);
        }

        public AssistantMessageViewHolder(Context ctx) {
            super(new PercentRelativeLayout(ctx));
            itemView.setPadding(0, 0, 0, 0);
            this.ctx = ctx;
        }

        private PercentRelativeLayout getWrapper() {
            return (PercentRelativeLayout) itemView;
        }

        public abstract void bind(AssistantMessage msg);

        protected void applyBubbleStyle(View view, AssistantMessage.Direction side) {
            if (view instanceof FlexboxLayout) {
                android.widget.Button btn = new android.widget.Button(ctx);
                view.setBackground(btn.getBackground());
                view.setStateListAnimator(btn.getStateListAnimator());
            } else if (side == AssistantMessage.Direction.FROM_SABRINA)
                view.setBackgroundResource(R.drawable.bubble_sabrina);
            else if (side == AssistantMessage.Direction.FROM_USER)
                view.setBackgroundResource(R.drawable.bubble_user);
        }

        protected void setIcon(AssistantMessage msg) {
            if (sabrinaHead == null)
                return;

            if (msg.icon == null)
                sabrinaHead.setImageDrawable(getSabrinaHeadBubble(ctx));
            else
                LoadImageTask.load(ctx, sabrinaHead, Config.S3_CLOUDFRONT_HOST + "/icons/" + msg.icon + ".png");
        }

        protected void setSideAndAlignment(View view, AssistantMessage msg) {
            AssistantMessage.Direction side = msg.direction;

            // override pickers, multiple choices and buttons
            if (msg.type.isInteraction()) {
                if (BuildConfig.DEBUG) {
                    if (side != AssistantMessage.Direction.FROM_SABRINA)
                        throw new AssertionError();
                }

                side = AssistantMessage.Direction.FROM_USER;
            }

            if (side == cachedSide)
                return;

            if (cachedSide != null)
                getWrapper().removeView(view);
            if (sabrinaHead != null)
                getWrapper().removeView(sabrinaHead);
            cachedSide = side;

            PercentRelativeLayout.LayoutParams params = new PercentRelativeLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);

            if (msg.type.isButton()) {
                params.getPercentLayoutInfo().widthPercent = 0.6f;
                if (view instanceof android.widget.Button)
                    ((android.widget.Button) view).setTransformationMethod(null);

                // indent the buttons
                if (side == AssistantMessage.Direction.FROM_SABRINA) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_START);
                    params.getPercentLayoutInfo().startMarginPercent = 0.05f;
                } else {
                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                    params.getPercentLayoutInfo().endMarginPercent = 0.05f;
                }
            } else {
                if (side == AssistantMessage.Direction.FROM_SABRINA) {
                    if (sabrinaHead == null) {
                        sabrinaHead = new ImageView(ctx);
                        sabrinaHead.setId(R.id.sabrina_head_bubble);
                    }
                    int forty_dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40.f, ctx.getResources().getDisplayMetrics()));
                    PercentRelativeLayout.LayoutParams headParams = new PercentRelativeLayout.LayoutParams(forty_dp, forty_dp);
                    headParams.setMarginStart(16);
                    headParams.topMargin = 16;
                    headParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    headParams.addRule(RelativeLayout.ALIGN_PARENT_START);
                    getWrapper().addView(sabrinaHead, headParams);

                    params.getPercentLayoutInfo().widthPercent = 0.8f;
                    params.addRule(RelativeLayout.RIGHT_OF, R.id.sabrina_head_bubble);
                    if (view instanceof TextView) {
                        ((TextView) view).setGravity(Gravity.START);
                        ((TextView) view).setTextIsSelectable(true);
                        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    }
                    view.setId(R.id.sabrina_chat_bubble);


                } else if (side == AssistantMessage.Direction.FROM_USER) {
                    params.getPercentLayoutInfo().widthPercent = 0.8f;
                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                    if (view instanceof TextView) {
                        ((TextView) view).setGravity(Gravity.END);
                        ((TextView) view).setTextIsSelectable(true);
                        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    }
                }
            }

            getWrapper().addView(view, params);
        }

        public static class Text extends AssistantMessageViewHolder {
            private TextView view;

            public Text(Context ctx) {
                super(ctx);
            }

            @Override
            public void bind(AssistantMessage msg) {
                if (view == null)
                    view = new TextView(ctx);
                view.setText(((AssistantMessage.Text) msg).msg);
                applyBubbleStyle(view, msg.direction);
                setSideAndAlignment(view, msg);
                setIcon(msg);
            }
        }

        public static class Picture extends AssistantMessageViewHolder {
            private ImageView view;
            private String cachedUrl;
            protected final MainActivity owner;

            public Picture(Context ctx, MainActivity owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(final AssistantMessage base) {
                if (view == null) {
                    view = new ImageView(ctx);
                    view.setBackgroundColor(Color.RED);
                    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    view.setAdjustViewBounds(true);
                }
                final AssistantMessage.Picture msg = (AssistantMessage.Picture)base;
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showPictureFullscreen(msg.url);
                    }
                });
                if (cachedUrl != msg.url) {
                    cachedUrl = msg.url;
                    Ion.with(ctx).load(msg.url).intoImageView(view);
                }
                applyBubbleStyle(view, msg.direction);
                setSideAndAlignment(view, msg);
                setIcon(msg);
            }
        }

        public static abstract class AbstractButton extends AssistantMessageViewHolder {
            protected android.widget.Button btn;
            protected final MainActivity owner;

            public AbstractButton(Context ctx, MainActivity owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage msg) {
                if (btn == null)
                    btn = new android.widget.Button(ctx);
                setSideAndAlignment(btn, msg);
                setIcon(msg);
            }
        }

        public static class RDL extends AssistantMessageViewHolder {
            private LinearLayout view;
            private TextView title;
            private TextView body;
            private final MainActivity owner;

            public RDL(Context ctx, MainActivity owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage base) {
                final AssistantMessage.RDL msg = (AssistantMessage.RDL)base;

                if (view == null) {
                    view = new LinearLayout(ctx);
                    view.setOrientation(LinearLayout.VERTICAL);
                    title = new TextView(ctx);
                    title.setTextAppearance(ctx, R.style.rdl_title);
                    body = new TextView(ctx);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    view.addView(title, params);
                    view.addView(body, params);
                }

                try {
                    title.setText(msg.rdl.optString("displayTitle"));
                    body.setText(msg.rdl.optString("displayText"));
                    String webCallback = msg.rdl.getString("webCallback");
                    final String url;
                    if (webCallback.startsWith("http"))
                        url = webCallback;
                    else
                        url = "http://" + webCallback;
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            owner.onLinkActivated(url);
                        }
                    });
                } catch (JSONException e) {
                    Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception while unpacking RDL", e);
                }
                applyBubbleStyle(view, msg.direction);
                setSideAndAlignment(view, msg);
                setIcon(msg);
            }
        }

        public static class Button extends AbstractButton {
            public Button(Context ctx, MainActivity owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.Button msg = (AssistantMessage.Button)base;
                btn.setText(msg.title);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.onButtonActivated(msg.title, msg.json);
                    }
                });
            }
        }

        public static class Link extends AbstractButton {
            public Link(Context ctx, MainActivity owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.Link msg = (AssistantMessage.Link)base;
                btn.setText(msg.title);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.onLinkActivated(msg.url);
                    }
                });
            }
        }

        public static class Choice extends AbstractButton {
            public Choice(Context ctx, MainActivity owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                final AssistantMessage.Choice msg = (AssistantMessage.Choice)base;
                btn.setText(msg.title);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.onChoiceActivated(msg.title, msg.idx);
                    }
                });
            }
        }

        public static class YesNo extends AssistantMessageViewHolder {
            private android.widget.Button yesbtn;
            private android.widget.Button nobtn;
            private LinearLayout yesno;
            private final MainActivity owner;

            public YesNo(Context ctx, MainActivity owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage msg) {
                if (yesno == null) {
                    yesno = new LinearLayout(ctx);
                    yesno.setOrientation(LinearLayout.HORIZONTAL);
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.weight = 1;
                if (yesbtn == null) {
                    yesbtn = new android.widget.Button(ctx);
                    yesbtn.setText(R.string.yes);
                    yesbtn.setLayoutParams(params);
                    yesbtn.setText(R.string.yes);
                    yesbtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            owner.onYesActivated();
                        }
                    });
                    yesno.addView(yesbtn);
                }
                if (nobtn == null) {
                    nobtn = new android.widget.Button(ctx);
                    nobtn.setLayoutParams(params);
                    nobtn.setText(R.string.no);
                    nobtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            owner.onNoActivated();
                        }
                    });
                    yesno.addView(nobtn);
                }

                setSideAndAlignment(yesno, msg);
                setIcon(msg);
            }
        }

        public static class SlotFilling extends AssistantMessageViewHolder {
            private FlexboxLayout slotFilling;
            private final MainActivity owner;

            public SlotFilling(Context ctx, MainActivity owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage base) {
                final AssistantMessage.SlotFilling msg = (AssistantMessage.SlotFilling) base;
                final Map<String, View> slots = new HashMap<>();

                if (slotFilling != null) {
                    slotFilling.removeAllViews();
                } else {
                    slotFilling = new FlexboxLayout(ctx);
                    slotFilling.setFlexWrap(FlexboxLayout.FLEX_WRAP_WRAP);
                    slotFilling.setAlignItems(FlexboxLayout.ALIGN_ITEMS_CENTER);
                    slotFilling.setJustifyContent(FlexboxLayout.JUSTIFY_CONTENT_CENTER);
                }

                String[] words = msg.title.split(" ");
                int idx = 0;
                int lastEditable = -1;
                for (String word : words) {
                    if (word.startsWith("$__person")) {
                        View slot = slotByType("String");
                        lastEditable = idx;
                        slots.put("__person", slot);
                        slotFilling.addView(slot);
                        if (word.equals("$__person's")) {
                            slotFilling.addView(btnStyleText("'s"));
                        }
                    } else if (word.startsWith("$")) {
                        String slotName = word.substring(1);
                        String slotType = msg.slotTypes.optString(slotName, "UNKNOWN");
                        View slot = slotByType(slotType);
                        if (slot instanceof EditText && slot.hasFocusable())
                            lastEditable = idx;
                        slots.put(slotName, slot);
                        slotFilling.addView(slot);
                    } else {
                        slotFilling.addView(btnStyleText(word));
                    }
                    idx += 1;
                }
                if (lastEditable != -1) {
                    EditText view = (EditText) slotFilling.getChildAt(lastEditable);
                    view.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                            slotFilling.performClick();
                            return false;
                        }
                    });
                }
                slotFilling.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Map<String, String> values = new HashMap<>();
                        for (Map.Entry<String, View> e : slots.entrySet()) {
                            View slot = e.getValue();
                            if (slot instanceof EditText)
                                values.put(e.getKey(), ((EditText)slot).getText().toString());
                            else if (slot instanceof Spinner)
                                values.put(e.getKey(), ((Spinner)slot).getSelectedItem().toString());
                            else
                                values.put(e.getKey(), "");
                        }
                        owner.onSlotFillingActivated(msg.title, msg.json, msg.slotTypes, values);
                    }
                });
                applyBubbleStyle(slotFilling, AssistantMessage.Direction.FROM_USER);
                setSideAndAlignment(slotFilling, msg);
                setIcon(msg);
            }

            private View slotByType(String type) {
                EditText et = new EditText(ctx);
                if (type == null) type = "UNKNOWN";
                switch(type) {
                    case "Number":
                        et.setInputType(InputType.TYPE_CLASS_NUMBER);
                        break;
                    case "Entity(tt:phone_number)":
                        et.setInputType(InputType.TYPE_CLASS_PHONE);
                        break;
                    case "Entity(tt:email_address)":
                        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
                        break;
                    case "Entity(tt:url)":
                        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                        break;
                    case "Boolean":
                        return enumSpinner("Enum(on,off)");
                    case "Location":
                    case "Measure":
                    case "Date":
                    case "Time":
                    // the following types are not supposed to appear here
                    case "Entity(tt:picture)":
                    case "Contact":
                    case "Choice":
                    case "List":
                    case "UNKNOWN":
                        et.setFocusable(false);
                        et.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                slotFilling.performClick();
                            }
                        });
                        break;
                    default:
                        if (type.startsWith("Enum"))
                            return enumSpinner(type);
                        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
                        break;
                }
                int wrapContent = LinearLayout.LayoutParams.WRAP_CONTENT;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wrapContent, wrapContent);
                et.setLayoutParams(lp);
                et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                et.setMinWidth(75);
                et.setBackgroundResource(android.R.drawable.editbox_background);
                et.setPadding(20, 5, 20, 5);
                et.setGravity(Gravity.CENTER);
                et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean hasFocus) {
                        if (hasFocus) {
                            ((MainActivity) ctx).findViewById(R.id.input_bar).setVisibility(View.GONE);
                        }
                    }
                });
                return et;
            }

            private TextView btnStyleText(String word) {
                android.widget.Button btn = new android.widget.Button(ctx);
                TextView tv = new TextView(ctx);
                tv.setTypeface(btn.getTypeface());
                tv.setTextColor(btn.getTextColors());
                tv.setText(word + " ");
                return tv;
            }

            private Spinner enumSpinner(String type) {
                List<String> options = new ArrayList<>(Arrays.asList(type.substring(5, type.length() - 1).split(",")));
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        ctx, android.R.layout.simple_spinner_item, options) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        TextView tv = ((TextView) v);
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        tv.setGravity(Gravity.CENTER);
                        tv.setPadding(0, 0, 0, 0);
                        return tv;
                    }
                };
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                Spinner spinner = new Spinner(ctx);
                spinner.setAdapter(adapter);
                spinner.setBackgroundResource(android.R.drawable.editbox_background);
                spinner.setPadding(0, 10, 0, 10);
                spinner.setGravity(Gravity.CENTER);
                return spinner;
            }
        }

        public static class ChooseLocation extends AbstractButton {
            public ChooseLocation(Context ctx, MainActivity owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_location);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showLocationPicker();
                    }
                });
            }
        }

        public static class ChoosePicture extends AbstractButton {
            public ChoosePicture(Context ctx, MainActivity owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_picture);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showImagePicker();
                    }
                });
            }
        }

        public static class ChooseContact extends AbstractButton {
            public ChooseContact(Context ctx, MainActivity owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_contact);

                final AssistantMessage.AskSpecial msg = (AssistantMessage.AskSpecial)base;
                final int requestCode = msg.what == AssistantMessage.AskSpecialType.PHONE_NUMBER ? MainActivity.REQUEST_PHONE_NUMBER : MainActivity.REQUEST_EMAIL;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        owner.showContactPicker(requestCode);
                    }
                });
            }
        }
    }

    private Context ctx;
    private AssistantHistoryModel history;

    public AssistantHistoryAdapter(MainActivity activity) {
        this.fragment = activity;
        setHasStableIds(false);
    }

    @Override
    public int getItemCount() {
        if (history == null)
            return 0;
        return history.size();
    }

    public AssistantMessage getItem(int position) {
        return history.get(position);
    }

    @Override
    public long getItemId(int position) {
        // AssistantMessages are immutable so we can hash them to find the item id
        return getItem(position).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        AssistantMessage msg = getItem(position);
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL)
            return 100 + ((AssistantMessage.AskSpecial)msg).what.ordinal();
        else
            return msg.type.ordinal();
    }

    @Override
    public void onBindViewHolder(AssistantMessageViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public AssistantMessageViewHolder onCreateViewHolder(ViewGroup group, int viewType) {
        AssistantMessage.Type type;
        AssistantMessage.AskSpecialType askSpecialType;
        if (viewType >= 100) {
            type = AssistantMessage.Type.ASK_SPECIAL;
            askSpecialType = AssistantMessage.AskSpecialType.values()[viewType-100];
        } else {
            type = AssistantMessage.Type.values()[viewType];
            askSpecialType = null;
        }

        switch (type) {
            case TEXT:
                return new AssistantMessageViewHolder.Text(getContext());
            case PICTURE:
                return new AssistantMessageViewHolder.Picture(getContext(), fragment);
            case RDL:
                return new AssistantMessageViewHolder.RDL(getContext(), fragment);
            case CHOICE:
                return new AssistantMessageViewHolder.Choice(getContext(), fragment);
            case LINK:
                return new AssistantMessageViewHolder.Link(getContext(), fragment);
            case BUTTON:
                return new AssistantMessageViewHolder.Button(getContext(), fragment);
            case SLOT_FILLING:
                return new AssistantMessageViewHolder.SlotFilling(getContext(), fragment);
            case ASK_SPECIAL:
                assert askSpecialType != null;
                switch (askSpecialType) {
                    case YESNO:
                        return new AssistantMessageViewHolder.YesNo(getContext(), fragment);

                    case LOCATION:
                        return new AssistantMessageViewHolder.ChooseLocation(getContext(), fragment);

                    case PICTURE:
                        return new AssistantMessageViewHolder.ChoosePicture(getContext(), fragment);

                    case PHONE_NUMBER:
                    case EMAIL_ADDRESS:
                        return new AssistantMessageViewHolder.ChooseContact(getContext(), fragment);

                    default:
                        // we don't recognize this, it should have been filtered by isFiltered()
                        throw new RuntimeException();
                }
            default:
                throw new RuntimeException();
        }
    }

    @Override
    public void onAdded(AssistantMessage msg, int idx) {
        notifyItemInserted(idx);
    }

    @Override
    public void onRemoved(AssistantMessage msg, int idx) {
        notifyItemRemoved(idx);
    }

    @Override
    public void onClear() {
        notifyDataSetChanged();
    }

    public void setContext(Context ctx) {
        this.ctx = ctx;
    }

    private Context getContext() {
        return ctx;
    }

    public void setHistory(AssistantHistoryModel history) {
        if (history == this.history)
            return;
        if (this.history != null)
            this.history.removeListener(this);
        this.history = history;
        if (history == null)
            return;
        history.addListener(this);

        notifyDataSetChanged();
    }
}
