package edu.stanford.thingengine.engine.jsapi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.thingengine.engine.ControlChannel;

/**
 * Created by gcampagn on 6/24/16.
 */
public class BluetoothAPI extends JavascriptAPI {
    public static final String LOG_TAG = "thingengine.Service";

    private final Context ctx;
    private final Handler handler;
    private final BluetoothAdapter adapter;

    private Receiver receiver;
    private volatile boolean discovering;

    private class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    onStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1),
                        intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1));
                    return;

                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    onDiscoveryFinished();
                    return;

                case BluetoothDevice.ACTION_FOUND:
                    onDeviceFound((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                    return;

                case BluetoothDevice.ACTION_CLASS_CHANGED:
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                case BluetoothDevice.ACTION_NAME_CHANGED:
                    onDeviceChanged((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                    return;
            }
        }
    }

    public BluetoothAPI(Context ctx, Handler handler, ControlChannel control) {
        super("Bluetooth", control);

        this.ctx = ctx;
        this.handler = handler;
        adapter = ((BluetoothManager)ctx.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        discovering = false;

        registerSync("start", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                start();
                return null;
            }
        });

        registerSync("stop", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                stop();
                return null;
            }
        });

        registerSync("startDiscovery", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                startDiscovery(((Number)args[0]).longValue());
                return null;
            }
        });

        registerSync("stopDiscovery", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                stopDiscovery();
                return null;
            }
        });
    }

    private void start() {
        receiver = new Receiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        ctx.registerReceiver(receiver, new IntentFilter(), null, handler);
    }

    private void stop() {
        ctx.unregisterReceiver(receiver);
        receiver = null;
    }

    private void onStateChanged(int newState, int oldState) {
        invokeAsync("onstatechanged", new int[] { newState, oldState });

        if (discovering && newState == BluetoothAdapter.STATE_ON)
            adapter.startDiscovery();
    }

    private void onDiscoveryFinished() {
        invokeAsync("ondiscoveryfinished", null);
    }

    private String[] uuidsToJson(ParcelUuid[] uuids) {
        String[] ret = new String[uuids.length];
        for (int i = 0; i < uuids.length; i++)
            ret[i] = uuids[i].getUuid().toString();
        return ret;
    }

    private JSONObject serializeBtDevice(BluetoothDevice device) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("address", device.getAddress().toLowerCase());
        obj.put("uuids", uuidsToJson(device.getUuids()));
        obj.put("class", device.getBluetoothClass().getDeviceClass());
        obj.put("alias", device.getName());
        obj.put("paired", device.getBondState() == BluetoothDevice.BOND_BONDED);
        return obj;
    }

    private void onDeviceFound(BluetoothDevice device) {
        try {
            invokeAsync("ondeviceadded", serializeBtDevice(device));
        } catch (JSONException e) {
            Log.i(LOG_TAG, "Failed to serialize Location", e);
        }
    }

    private void onDeviceChanged(BluetoothDevice device) {
        try {
            invokeAsync("ondevicechanged", serializeBtDevice(device));
        } catch (JSONException e) {
            Log.i(LOG_TAG, "Failed to serialize Location", e);
        }
    }

    private void startDiscovery(long timeout) {
        discovering = true;
        if (!adapter.startDiscovery())
            ctx.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopDiscovery();
            }
        }, timeout);
    }

    private void stopDiscovery() {
        discovering = false;
        adapter.cancelDiscovery();
    }
}
