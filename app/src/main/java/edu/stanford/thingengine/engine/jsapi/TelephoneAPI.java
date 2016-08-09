package edu.stanford.thingengine.engine.jsapi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;

import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.service.EngineService;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 8/8/16.
 */
public class TelephoneAPI extends JavascriptAPI {
    private final EngineService ctx;

    public TelephoneAPI(EngineService ctx, ControlChannel control) {
        super("Telephone", control);

        this.ctx = ctx;

        registerAsync("call", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                call((String)args[0]);
                return null;
            }
        });

        registerAsync("callEmergency", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                callEmergency();
                return null;
            }
        });
    }

    private void requestPermission() throws InterruptedException {
        InteractionCallback callback = ctx.getInteractionCallback();
        if (callback == null)
            return;

        callback.requestPermission(Manifest.permission.CALL_PHONE, InteractionCallback.REQUEST_CALL);
    }

    private void call(String number) throws InterruptedException {
        int permissionCheck = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
            requestPermission();

        Uri uri = Uri.parse("tel:" + number);
        Intent intent = new Intent(Intent.ACTION_CALL, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private static final String[] EMERGENCY_NUMBERS = { "911", "999", "113", "112" };

    private void callEmergency() {
        String number = null;
        for (String candidate : EMERGENCY_NUMBERS) {
            if (PhoneNumberUtils.isLocalEmergencyNumber(ctx, candidate)) {
                number = candidate;
                break;
            }
        }
        if (number == null)
            number = "112"; // 112 is the GSM standard and should work everywhere

        Uri uri = Uri.parse("tel:" + number);
        Intent intent = new Intent(Intent.ACTION_DIAL, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
