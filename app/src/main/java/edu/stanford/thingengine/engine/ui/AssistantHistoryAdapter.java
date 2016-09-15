package edu.stanford.thingengine.engine.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.percent.PercentRelativeLayout;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantHistoryModel;
import edu.stanford.thingengine.engine.service.AssistantMessage;

/**
 * Created by gcampagn on 8/22/16.
 */
class AssistantHistoryAdapter extends RecyclerView.Adapter<AssistantHistoryAdapter.AssistantMessageViewHolder> implements AssistantHistoryModel.Listener {
    private AssistantFragment fragment;

    public abstract static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        protected final Context ctx;
        private final ThingpediaClient mThingpedia;
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
            mThingpedia = new ThingpediaClient(ctx);
        }

        private PercentRelativeLayout getWrapper() {
            return (PercentRelativeLayout) itemView;
        }

        public abstract void bind(AssistantMessage msg);

        protected void applyBubbleStyle(View view, AssistantMessage.Direction side) {
            if (view instanceof FlexboxLayout) {
                view.setBackgroundResource(android.R.drawable.btn_default);
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

        private class SlotArgs {
            public String[] types;
            public AssistantMessage.SlotFilling msg;
            public FlexboxLayout view;
            public AssistantFragment owner;
        }

        protected class GetSlotFillingTask extends AsyncTask<SlotArgs, Void, SlotArgs> {
            @Override
            protected SlotArgs doInBackground(SlotArgs... args) {
                SlotArgs arg = args[0];
                try {
                    JSONObject obj = new JSONObject(arg.msg.json);
                    String cmdType = obj.keys().next();
                    String id = obj.getJSONObject(cmdType).getJSONObject("name").getString("id");
                    String kind = id.substring(3, id.indexOf("."));
                    String cmd = id.substring(id.indexOf(".") + 1);
                    JSONArray slots = obj.getJSONObject(cmdType).getJSONArray("slots");
                    JSONObject meta = mThingpedia.getMeta(kind);
                    JSONObject cmdmeta = meta.getJSONObject(meta.keys().next())
                            .getJSONObject(getTypeName(cmdType))
                            .getJSONObject(cmd);
                    JSONArray schema = cmdmeta.getJSONArray("schema");
                    JSONArray argNames = cmdmeta.getJSONArray("args");
                    String[] types = new String[schema.length()];
                    for (int i = 0; i < slots.length(); i++) {
                        types[i] = getSlotType(slots.getString(i), schema, argNames);
                    }
                    arg.types = types;
                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                }
                return arg;
            }

            @Override
            public void onPostExecute(SlotArgs args) {
                FlexboxLayout slotFilling = args.view;
                final AssistantMessage.SlotFilling msg = args.msg;
                final AssistantFragment owner = args.owner;
                final String[] types = args.types;
                int slotIndex = 0;

                if (slotFilling != null) {
                    slotFilling.removeAllViews();
                } else {
                    slotFilling = new FlexboxLayout(ctx);
                    slotFilling.setFlexWrap(FlexboxLayout.FLEX_WRAP_WRAP);
                    slotFilling.setAlignItems(FlexboxLayout.ALIGN_ITEMS_CENTER);
                }

                final List<View> slots = new ArrayList();

                int lastIndex = 0;
                int currentIndex;
                while(true){
                    currentIndex = msg.title.indexOf("____", lastIndex);
                    if (currentIndex == -1) {
                        String[] words = msg.title.substring(lastIndex).split(" ");
                        for (String word: words) {
                            TextView tv = new TextView(ctx);
                            tv.setText(word + " ");
                            slotFilling.addView(tv);
                        }
                        break;
                    }
                    if (currentIndex != lastIndex) {
                        String[] words = msg.title.substring(lastIndex, currentIndex).split(" ");
                        for (String word: words) {
                            TextView tv = new TextView(ctx);
                            tv.setText(word + " ");
                            slotFilling.addView(tv);
                        }
                    }
                    View slot = editTextByType(types[slotIndex]);
                    slotIndex ++;
                    slots.add(slot);
                    slotFilling.addView(slot);
                    lastIndex = currentIndex + 4;
                }
                slotFilling.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String[] values = new String[slots.size()];
                        for (int i = 0; i < slots.size(); i++) {
                            View slot = slots.get(i);
                            if (slot instanceof EditText)
                                values[i] = (((EditText)slot).getText().toString());
                            else if (slot instanceof Spinner)
                                values[i] = ((Spinner)slot).getSelectedItem().toString();
                            Log.d("SLOT_FILLING", values[i]);
                        }
                        owner.onSlotFillingActivated(msg.title, msg.json, values, types);
                    }
                });
                applyBubbleStyle(slotFilling, AssistantMessage.Direction.FROM_USER);
                setSideAndAlignment(slotFilling, msg);
                setIcon(msg);
            }

            private String getTypeName(String type) {
                if (type.equals("query"))
                    return "queries";
                else
                    return type + "s";
            }

            private String getSlotType(String argName, JSONArray schema, JSONArray argNames) {
                try {
                    for (int i = 0; i < argNames.length(); i++) {
                        if (argNames.getString(i).equals(argName))
                            return schema.getString(i);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return "String";
            }

            private View editTextByType(String type) {
                if (type.startsWith("Enum"))
                    return enumSpinner(type);
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                EditText et = new EditText(ctx);
                et.setLayoutParams(lp);
                et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                et.setMinWidth(75);
                et.setBackgroundResource(android.R.drawable.editbox_background);
                et.setPadding(10, 5, 10, 5);
                switch(type) {
                    case "Number":
                        et.setInputType(InputType.TYPE_CLASS_NUMBER);
                        break;
                    case "PhoneNumber":
                        et.setInputType(InputType.TYPE_CLASS_PHONE);
                        break;
                    case "EmailAddress":
                        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
                        break;
                    case "Picture":
                    case "Location":
                    case "Measure":
                    case "Date":
                    case "Time":
                    case "Contact":
                    case "Choice":
                    case "Bool":
                        et.setEnabled(false);
                    default:
                        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
                        break;
                }
                return et;
            }

            private Spinner enumSpinner(String type) {
                List<String> options = new ArrayList(Arrays.asList(type.substring(5, type.length() - 1).split(",")));
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        ctx, android.R.layout.simple_spinner_item, options) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        TextView tv = ((TextView) v);
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        return tv;
                    }
                };
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                Spinner spinner = new Spinner(ctx);
                spinner.setAdapter(adapter);
                spinner.setBackgroundResource(android.R.drawable.editbox_background);
                return spinner;
            }
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
            protected final AssistantFragment owner;

            public Picture(Context ctx, AssistantFragment owner) {
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
            protected final AssistantFragment owner;

            public AbstractButton(Context ctx, AssistantFragment owner) {
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
            private final AssistantFragment owner;

            public RDL(Context ctx, AssistantFragment owner) {
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
            public Button(Context ctx, AssistantFragment owner) {
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
            public Link(Context ctx, AssistantFragment owner) {
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
            public Choice(Context ctx, AssistantFragment owner) {
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
            private final AssistantFragment owner;

            public YesNo(Context ctx, AssistantFragment owner) {
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
            private final AssistantFragment owner;

            public SlotFilling(Context ctx, AssistantFragment owner) {
                super(ctx);
                this.owner = owner;
            }

            @Override
            public void bind(AssistantMessage base) {
                AssistantMessage.SlotFilling msg = (AssistantMessage.SlotFilling) base;
                SlotArgs args = new SlotArgs();
                args.msg = msg;
                args.owner = this.owner;
                args.view = slotFilling;
                new GetSlotFillingTask().execute(args);
            }
        }

        public static class ChooseLocation extends AbstractButton {
            public ChooseLocation(Context ctx, AssistantFragment owner) {
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
            public ChoosePicture(Context ctx, AssistantFragment owner) {
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
            public ChooseContact(Context ctx, AssistantFragment owner) {
                super(ctx, owner);
            }

            @Override
            public void bind(AssistantMessage base) {
                super.bind(base);
                btn.setText(R.string.btn_choose_contact);

                final AssistantMessage.AskSpecial msg = (AssistantMessage.AskSpecial)base;
                final int requestCode = msg.what == AssistantMessage.AskSpecialType.PHONE_NUMBER ? AssistantFragment.REQUEST_PHONE_NUMBER : AssistantFragment.REQUEST_EMAIL;
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

    public AssistantHistoryAdapter(AssistantFragment fragment) {
        this.fragment = fragment;
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
