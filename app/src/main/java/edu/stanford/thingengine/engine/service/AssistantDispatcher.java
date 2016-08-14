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

import org.json.JSONException;
import org.json.JSONObject;

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

        if (output != null)
            notificationMessages.clear();
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

    private abstract class CommandTask extends AsyncTask<String, Void, Void> {
        @Override
        public void onPreExecute() {
            if (callbacks != null)
                callbacks.onBeforeCommand();
        }

        protected abstract void run(String command);

        @Override
        protected Void doInBackground(String... params) {
            run(params[0]);
            return null;
        }

        @Override
        public void onPostExecute(Void unused) {
            if (callbacks != null)
                callbacks.onAfterCommand();
        }
    }

    public AssistantMessage.Text handleCommand(String command) {
        if (BuildConfig.DEBUG) {
            if (command.startsWith("\\r ")) {
                String json = command.substring(3);
                handleParsedCommand(json);
                return null;
            }
        }

        (new CommandTask() {
            @Override
            protected void run(String command) {
                cmdHandler.handleCommand(command);
            }
        }).executeOnExecutor(async, command);

        AssistantMessage.Text text = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, command);
        history.add(text);
        return text;
    }

    public void handleParsedCommand(final String json) {
        (new CommandTask() {
            @Override
            protected void run(String command) {
                cmdHandler.handleParsedCommand(command);
            }
        }).executeOnExecutor(async, json);
    }

    public void handleClear() {
        (new CommandTask() {
            @Override
            protected void run(String command) {
                cmdHandler.handleParsedCommand(command);
            }

            @Override
            public void onPostExecute(Void unused) {
                history.clear();
                super.onPostExecute(unused);
            }
        }).executeOnExecutor(async, "{\"special\":\"tt:root.special.nevermind\"}");
    }

    public void handleHelp() {
        handleParsedCommand("{\"special\":\"tt:root.special.help\"}");
    }

    public void handleDiscover(String discoveryType, String kind, String name) {
        try {
            JSONObject obj = new JSONObject();
            JSONObject inner = new JSONObject();
            obj.put("discover", inner);
            inner.put("type", discoveryType);
            inner.put("kind", kind);
            inner.put("text", name);

            handleParsedCommand(obj.toString());
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing choice JSON", e);
        }
    }

    public void handleChoice(int idx) {
        try {
            JSONObject obj = new JSONObject();
            JSONObject inner = new JSONObject();
            obj.put("answer", inner);
            inner.put("type", "Choice");
            inner.put("value", idx);

            handleParsedCommand(obj.toString());
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing choice JSON", e);
        }
    }

    public AssistantMessage handleLocation(Place place) {
        try {
            JSONObject obj = new JSONObject();
            JSONObject inner = new JSONObject();
            obj.put("answer", inner);
            inner.put("type", "Location");
            JSONObject location = new JSONObject();
            inner.put("value", location);
            LatLng latLng = place.getLatLng();
            location.put("relativeTag", "absolute");
            location.put("longitude", latLng.longitude);
            location.put("latitude", latLng.latitude);
            location.put("display", place.getName());

            handleParsedCommand(obj.toString());
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing location JSON", e);
        }

        AssistantMessage.Text loc = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, place.getName());
        history.add(loc);
        return loc;
    }

    public AssistantMessage handlePicture(String url) {
        try {
            JSONObject obj = new JSONObject();
            JSONObject inner = new JSONObject();
            obj.put("answer", inner);
            inner.put("type", "Picture");
            JSONObject picture = new JSONObject();
            inner.put("value", picture);
            picture.put("value", url);

            handleParsedCommand(obj.toString());
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing picture JSON", e);
        }

        AssistantMessage.Picture pic = new AssistantMessage.Picture(AssistantMessage.Direction.FROM_USER, url);
        history.add(pic);
        return pic;
    }

    public AssistantMessage handleContact(String data, String displayName, String type) {
        try {
            JSONObject obj = new JSONObject();
            JSONObject inner = new JSONObject();
            obj.put("answer", inner);
            inner.put("type", type);
            JSONObject value = new JSONObject();
            inner.put("value", value);
            value.put("value", data);
            value.put("display", displayName);

            handleParsedCommand(obj.toString());
        } catch(JSONException e) {
            Log.e(EngineService.LOG_TAG, "Unexpected json exception while constructing picture JSON", e);
        }

        AssistantMessage.Text contact = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, displayName);
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

        if (!maybeInformUI(msg))
            maybeNotify(msg);

        return true;
    }

    private void maybeNotify(AssistantMessage msg) {
        notificationMessages.add(msg);

        Notification.Builder builder = new Notification.Builder(ctx);
        builder.setSmallIcon(R.drawable.sabrina);
        builder.setAutoCancel(true);
        builder.setVisibility(Notification.VISIBILITY_PRIVATE);
        builder.setPublicVersion(new Notification.Builder(ctx)
                .setSmallIcon(R.drawable.sabrina)
                .setAutoCancel(true)
                .setContentTitle("Sabrina says something")
                .setContentText("Content hidden for privacy")
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
                builder.setContentTitle("Sabrina Says");
                break;
            case PICTURE:
                builder.setContentTitle("Sabrina Sends a Picture");
                break;
            case CHOICE:
            case BUTTON:
                builder.setContentTitle("Sabrina Sends a Button");
                break;
            case RDL:
            case LINK:
                builder.setContentTitle("Sabrina Sends a Link");
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
                    .setSummaryText("Sabrina Says...")
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
        assistantHandler.obtainMessage(MSG_ASSISTANT_MESSAGE, msg).sendToTarget();
    }
}
