// This file is part of Almond
//
// Copyright 2016-2018 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.ui.MainActivity;

/**
 * Created by gcampagn on 7/10/16.
 */
public class AssistantDispatcher implements Handler.Callback {
    private static final int MSG_ASSISTANT_MESSAGE = 1;

    private static final int NOTIFICATION_ID = 42;

    private final Executor async = Executors.newSingleThreadExecutor();
    private final AssistantHistoryModel history = new AssistantHistoryModel();
    private final Context ctx;
    private final Handler assistantHandler;
    private final AssistantCommandHandler cmdHandler;

    private long lastNotificationTime = -1;
    private final List<AssistantMessage> notificationMessages = new ArrayList<>();
    private AssistantMessage.AskSpecial asking;
    private AssistantOutput output;
    private AssistantLifecycleCallbacks callbacks;

    public AssistantDispatcher(Context ctx, AssistantCommandHandler cmdHandler) {
        this.ctx = ctx;
        this.cmdHandler = cmdHandler;
        assistantHandler = new Handler(Looper.getMainLooper(), this);
    }

    // to be called from the main thread
    public void setAssistantOutput(AssistantOutput output) {
        this.output = output;

        if (output != null) {
            notificationMessages.clear();

            NotificationManager mgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            mgr.cancel(NOTIFICATION_ID);

            if (asking != null)
                output.display(asking);
        }
    }

    public void setAssistantCallbacks(AssistantLifecycleCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void ready() {
        async.execute(new Runnable() {
            @Override
            public void run() {
                cmdHandler.ready();
            }
        });
    }

    private abstract class CommandTask<E> extends AsyncTask<E, Void, Void> {
        @Override
        public void onPreExecute() {
            if (callbacks != null)
                callbacks.onBeforeCommand();
        }

        protected abstract void run(E command);

        @Override
        @SafeVarargs
        protected final Void doInBackground(E... params) {
            run(params[0]);
            return null;
        }

        @Override
        public void onPostExecute(Void unused) {
            if (callbacks != null)
                callbacks.onAfterCommand();
        }
    }

    private void handleSlashR(String line) {
        if (line.startsWith("{")) {
            try {
                JSONObject json = (JSONObject) (new JSONTokener(line)).nextValue();
                handleParsedCommand(json);
            } catch (JSONException e) {
                Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing location JSON", e);
            }
        } else {
            handleParsedCommand(line.split("\\s+"));
        }
    }

    public AssistantMessage.Text handleCommand(String command) {
        if (BuildConfig.DEBUG) {
            if (command.startsWith("\\r ")) {
                String line = command.substring(3);
                handleSlashR(line);
                return null;
            } else if (command.startsWith("\\t ")) {
                handleThingTalk(command.substring(3));
                return null;
            }
        }

        (new CommandTask<String>() {
            @Override
            protected void run(String command) {
                cmdHandler.handleCommand(command);
            }
        }).executeOnExecutor(async, command);

        AssistantMessage.Text text = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, command);
        history.removeButtons();
        history.add(text);
        return text;
    }

    public void presentExample(String utterance, String targetCode) {
        collapseButtons();

        (new AsyncTask<String, Void, Void>() {
            @Override
            public void onPreExecute() {
                if (callbacks != null)
                    callbacks.onBeforeCommand();
            }

            @Override
            protected final Void doInBackground(String... params) {
                cmdHandler.presentExample(params[0], params[1]);
                return null;
            }

            @Override
            public void onPostExecute(Void unused) {
                if (callbacks != null)
                    callbacks.onAfterCommand();
            }
        }).executeOnExecutor(async, utterance, targetCode);
    }

    private JSONArray arrayToJSON(Object[] array) {
        JSONArray arr = new JSONArray();
        for (Object el : array)
            arr.put(el);
        return arr;
    }

    private void handleThingTalk(final String code) {
        (new CommandTask<String>() {
            @Override
            protected void run(String code) {
                cmdHandler.handleThingTalk(code);
            }
        }).executeOnExecutor(async, code);
    }

    private void handleParsedCommand(final JSONObject json) {
        (new CommandTask<JSONObject>() {
            @Override
            protected void run(JSONObject json) {
                cmdHandler.handleParsedCommand(json);
            }
        }).executeOnExecutor(async, json);
    }

    private void handleParsedCommand(final String[] code, final JSONObject entities) {
        JSONObject json = new JSONObject();
        try {
            json.put("code", arrayToJSON(code));
            json.put("entities", entities);
        } catch(JSONException e) {
            throw new RuntimeException(e);
        }

        handleParsedCommand(json);
    }
    private void handleParsedCommand(final String... code) {
        handleParsedCommand(code, new JSONObject());
    }

    public void collapseButtons() {
        history.removeButtons();
    }

    private String[] specialCode(String specialType) {
        return new String[] { "bookkeeping", "special", "special:" + specialType };
    }

    public void handleClear() {
        (new CommandTask<Object>() {
            @Override
            protected void run(Object unused) {
                JSONObject json = new JSONObject();
                try {
                    json.put("code", new JSONArray(specialCode("nevermind")));
                    json.put("entities", new JSONObject());
                } catch(JSONException e) {
                    throw new RuntimeException(e);
                }

                cmdHandler.handleParsedCommand(json);
            }

            @Override
            public void onPostExecute(Void unused) {
                history.clear();
                super.onPostExecute(unused);
            }
        }).executeOnExecutor(async, new Object());
    }

    public void handleHelp() {
        handleParsedCommand(specialCode("help"));
        history.removeButtons();
    }

    public void handleTrain() {
        handleParsedCommand(specialCode("train"));
        history.removeButtons();
    }

    public void handleMakeRule() {
        handleParsedCommand(specialCode("makerule"));
        history.removeButtons();
    }

    public AssistantMessage handleNeverMind() {
        handleParsedCommand(specialCode("nevermind"));

        AssistantMessage msg = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, ctx.getString(R.string.never_mind));
        history.removeButtons();
        history.add(msg);
        return msg;
    }

    public AssistantMessage handleYes() {
        handleParsedCommand(specialCode("yes"));

        AssistantMessage.Text msg = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, ctx.getString(R.string.yes));
        history.removeButtons();
        history.add(msg);
        return msg;
    }

    public AssistantMessage handleNo() {
        handleParsedCommand(specialCode("no"));

        AssistantMessage.Text msg = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, ctx.getString(R.string.no));
        history.removeButtons();
        history.add(msg);
        return msg;
    }


    public void handleConfigure(String kind) {
        handleParsedCommand("now", "=>", "@org.thingpedia.builtin.thingengine.builtin.configure",
                "param:device:Entity(tt:device)", "=", "device:" + kind);
        history.removeButtons();
    }

    public AssistantMessage handleChoice(String title, int idx) {
        handleParsedCommand("bookkeeping", "choice", Integer.toString(idx));

        AssistantMessage.Text msg = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, title);
        history.removeButtons();
        history.add(msg);
        return msg;
    }

    public AssistantMessage handleButton(String title, JSONObject json) {
        handleParsedCommand(json);

        AssistantMessage.Text msg = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, title);
        history.removeButtons();
        history.add(msg);
        return msg;
    }

    public AssistantMessage handleLocation(Place place) {
        try {
            JSONObject location = new JSONObject();
            LatLng latLng = place.getLatLng();
            location.put("relativeTag", "absolute");
            location.put("longitude", latLng.longitude);
            location.put("latitude", latLng.latitude);
            location.put("display", place.getName());

            JSONObject entities = new JSONObject();
            entities.put("LOCATION_0", location);

            handleParsedCommand(new String[] {"bookkeeping", "answer", "LOCATION_0"}, entities);
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing location JSON", e);
        }

        AssistantMessage.Text loc = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, place.getName());
        history.removeButtons();
        history.add(loc);
        return loc;
    }

    public AssistantMessage handlePicture(String url) {
        try {
            JSONObject entities = new JSONObject();
            entities.put("PICTURE_0", url);

            handleParsedCommand(new String[] {"bookkeeping", "answer", "PICTURE_0"}, entities);
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing picture JSON", e);
        }

        AssistantMessage.Picture pic = new AssistantMessage.Picture(AssistantMessage.Direction.FROM_USER, null, url);
        history.removeButtons();
        history.add(pic);
        return pic;
    }

    public AssistantMessage handleContact(String data, String displayName, String type) {
        try {
            JSONObject contact = new JSONObject();
            contact.put("value", data);
            contact.put("display", displayName);

            JSONObject entities = new JSONObject();
            entities.put("GENERIC_ENTITY_" + type + "_0", contact);

            handleParsedCommand(new String[] {"bookkeeping", "answer", "GENERIC_ENTITY_" + type + "_0"}, entities);
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing contact JSON", e);
        }

        AssistantMessage.Text contact = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, null, displayName);
        history.removeButtons();
        history.add(contact);
        return contact;
    }

    public AssistantHistoryModel getHistory() {
        return history;
    }

    @Override
    public boolean handleMessage(Message m) {
        if (m.what != MSG_ASSISTANT_MESSAGE)
            return false;

        AssistantMessage msg = (AssistantMessage) m.obj;
        history.add(msg);

        if (msg instanceof AssistantMessage.AskSpecial)
            asking = (AssistantMessage.AskSpecial)msg;

        if (!maybeInformUI(msg))
            maybeNotify(msg);

        return true;
    }

    private void maybeNotify(AssistantMessage msg) {
        if (!msg.shouldNotify())
            return;

        notificationMessages.add(msg);

        Notification.Builder builder = new Notification.Builder(ctx);
        builder.setSmallIcon(R.drawable.sabrina_head);
        builder.setAutoCancel(true);
        builder.setVisibility(Notification.VISIBILITY_PRIVATE);
        builder.setPublicVersion(new Notification.Builder(ctx)
                .setSmallIcon(R.drawable.sabrina_head)
                .setAutoCancel(true)
                .setContentTitle(ctx.getString(R.string.sabrina_says_something))
                .setContentText(ctx.getString(R.string.content_hidden))
                .build()
        );
        builder.setLights(Color.WHITE, 500, 500);

        long currentTime = System.currentTimeMillis();
        // don't play sound/vibrate if the notification is still there and we
        // played it in the last 30s
        if (lastNotificationTime >= 0 && currentTime - lastNotificationTime < 30000)
            builder.setOnlyAlertOnce(true);
        lastNotificationTime = currentTime;

        AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        switch (audioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                // fall through
            case AudioManager.RINGER_MODE_VIBRATE:
                // vibrate once, for 1000ms
                builder.setVibrate(new long[] { 0, 1000 });
                break;
            default:
        }

        switch (msg.type) {
            case TEXT:
                builder.setContentTitle(ctx.getString(R.string.sabrina_says));
                break;
            case PICTURE:
                builder.setContentTitle(ctx.getString(R.string.sabrina_sends_picture));
                break;
            case CHOICE:
            case BUTTON:
                builder.setContentTitle(ctx.getString(R.string.sabrina_sends_button));
                break;
            case RDL:
            case LINK:
                builder.setContentTitle(ctx.getString(R.string.sabrina_sends_link));
                break;
        }
        builder.setContentText(msg.toText());

        if (notificationMessages.size() > 1) {
            StringBuilder body = new StringBuilder();
            for (AssistantMessage msg2 : notificationMessages) {
                if (body.length() > 0)
                    body.append('\n');
                body.append(msg2.toText());
            }
            builder.setStyle(new Notification.BigTextStyle()
                    .setSummaryText(ctx.getString(R.string.sabrina_says_dotdotdot))
                    .bigText(body));
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        NotificationManager mgr = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        mgr.notify(NOTIFICATION_ID, builder.build());
    }

    private boolean maybeInformUI(AssistantMessage msg) {
        if (output != null) {
            output.display(msg);
            return true;
        } else {
            return false;
        }
    }

    // to be called from any thread
    public void dispatch(AssistantMessage msg) {
        if (isSlotFilling(msg))
            dispatchSlotFilling((AssistantMessage.Button)msg);
        else
            assistantHandler.obtainMessage(MSG_ASSISTANT_MESSAGE, msg).sendToTarget();
    }

    private boolean isSlotFilling(AssistantMessage msg) {
        if (msg.type == AssistantMessage.Type.BUTTON) {
            JSONObject jsonObj = ((AssistantMessage.Button) msg).json;
            if (jsonObj.has("slots"))
                return true;
        }
        return false;
    }

    private void dispatchSlotFilling(AssistantMessage.Button msg) {
        try {
            AssistantMessage slotFilling = new AssistantMessage.SlotFilling(
                    msg.direction, msg.title, msg.json);
            assistantHandler.obtainMessage(MSG_ASSISTANT_MESSAGE, slotFilling).sendToTarget();
        } catch (JSONException e) {
            Log.e(EngineService.LOG_TAG, "Failed to parse button JSON", e);
            assistantHandler.obtainMessage(MSG_ASSISTANT_MESSAGE, msg).sendToTarget();
        }
    }
}
