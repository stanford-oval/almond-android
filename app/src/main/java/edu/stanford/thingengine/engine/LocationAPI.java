package edu.stanford.thingengine.engine;

import android.content.Context;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

public class LocationAPI extends JavascriptAPI {
    private Location location;
    private LocationManager manager;

    public LocationAPI(ControlChannel control) {
        registerAsync("getLocation", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                /* args[0] is a callback function */
                getLocation(args[0]);
                return null;
            }
        });

        initializeLocation();
    }
    private void initializeLocation(){
        manager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        String bestProvider = manager.getBestProvider(new Criteria(), false);
        location = null;

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location newLocation) {
                location = newLocation;
            }
        };

        try {
            manager.requestSingleUpdate(bestProvider, GPSListener , null);
        } catch( SecurityException e ){}
    }
    private void getLocation(/* What type should this be? */ cb) {

        location = manager.getLastKnownLocation(bestProvider);

        if(location == null)
            cb(0,0);
        else
            cb(location.getLongitude(), location.getLatitude());

        return null;
    }
}
