package edu.stanford.thingengine.engine.jsapi;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.provider.Telephony;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.service.EngineService;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 5/16/16.
 */
public final class SmsAPI extends JavascriptAPI {
    public static final String LOG_TAG = "thingengine.Service";

    private final EngineService ctx;
    private final Handler handler;
    private final BroadcastReceiver receiver;

    private class SmsBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            for (SmsMessage message : messages)
                notifySms(message);
        }
    }

    public SmsAPI(Handler handler, EngineService ctx, ControlChannel control) {
        super("Sms", control);

        this.ctx = ctx;
        this.handler = handler;
        this.receiver = new SmsBroadcastReceiver();

        this.registerAsync("sendMessage", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                sendMessage((String)args[0], (String)args[1]);
                return null;
            }
        });

        registerAsync("start", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                start();
                return null;
            }
        });

        registerAsync("stop", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                stop();
                return null;
            }
        });
    }

    private void sendMessage(String phoneNumber, String message) throws InterruptedException {
        SmsManager smsManager = SmsManager.getDefault();

        int permissionCheck = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
            requestPermission();

        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    private void requestPermission() throws InterruptedException {
        InteractionCallback callback = ctx.getInteractionCallback();
        if (callback == null)
            return;

        callback.requestPermission(Manifest.permission.SEND_SMS, InteractionCallback.REQUEST_SMS);
    }

    private void start() {
        IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);

        ctx.registerReceiver(receiver, filter, null, handler);
    }

    private void stop() {
        ctx.unregisterReceiver(receiver);
    }

    private void notifySms(SmsMessage message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("from", message.getOriginatingAddress());
            obj.put("body", message.getMessageBody());
            invokeAsync("onsmsreceived", obj);
        } catch(JSONException e) {
            Log.i(LOG_TAG, "Failed to serialize SMS", e);
        }
    }
}
