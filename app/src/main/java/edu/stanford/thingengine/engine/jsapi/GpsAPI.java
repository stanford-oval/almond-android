package edu.stanford.thingengine.engine.jsapi;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 5/6/16.
 */
public class GpsAPI extends JavascriptAPI {
    public static final String LOG_TAG = "thingengine.Service";

    private final Context context;
    private final Handler handler;
    private final GpsLocationCallback callback;
    private final GoogleApiClient mGoogleApiClient;

    private class GpsLocationCallback extends LocationCallback {
        @Override
        public void onLocationResult(LocationResult result) {
            reportLocation(result.getLastLocation());
        }
    }

    public GpsAPI(Handler handler, Context context, ControlChannel control) {
        super("Gps", control);
        this.handler = handler;
        this.context = context;
        this.callback = new GpsLocationCallback();
        this.mGoogleApiClient = new GoogleApiClient.Builder(this.context)
                .addApi(LocationServices.API)
                .setHandler(this.handler)
                .build();

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

    private void reportLocation(@Nullable Location location) {
        try {
            if (location != null) {
                JSONObject jsonLocation = new JSONObject();

                jsonLocation.put("latitude", location.getLatitude());
                jsonLocation.put("longitude", location.getLongitude());
                jsonLocation.put("altitude", location.getAltitude());
                jsonLocation.put("bearing", location.getBearing());
                jsonLocation.put("provider", location.getProvider());
                jsonLocation.put("speed", location.getSpeed());
                jsonLocation.put("time", location.getTime());

                invokeAsync("onlocationchanged", jsonLocation);
            } else {
                invokeAsync("onlocationchanged", null);
            }
        } catch(JSONException e) {
            Log.i(LOG_TAG, "Failed to serialize Location", e);
        }
    }

    private LocationRequest requestLocationSync() throws InterruptedException {
        LocationRequest request = new LocationRequest();
        // don't request updates more frequently than every 30 seconds
        request.setInterval(30000);
        // rate limit ourselves because the server is ratelimited anyway
        request.setFastestInterval(5000);
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(request);

        PendingResult<LocationSettingsResult> pendingResult =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());
        LocationSettingsResult result = pendingResult.await();

        final Status status = result.getStatus();

        InteractionCallback callback = getControl().getInteractionCallback();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                break;

            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                if (callback != null) {
                    if (callback.resolveResult(status, InteractionCallback.ENABLE_GPS)) {
                        break;
                    }
                }
                /* fallback */

            default:
                throw new SecurityException("Location services are disabled by the user");
        }

        return request;
    }

    private void start() throws InterruptedException, IOException {
        ConnectionResult result = mGoogleApiClient.blockingConnect();
        if (!result.isSuccess())
            throw new IOException("Failed to connect to Google Play Services: " + result.getErrorMessage());
        LocationRequest request = requestLocationSync();
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this.callback, this.handler.getLooper());
        reportLocation(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
    }

    private void stop() {
        if (!mGoogleApiClient.isConnected())
            return;

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this.callback);
        mGoogleApiClient.disconnect();
    }
}
