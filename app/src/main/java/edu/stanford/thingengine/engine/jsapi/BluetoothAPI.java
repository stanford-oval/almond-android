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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.service.EngineService;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 6/24/16.
 */
public class BluetoothAPI extends JavascriptAPI {
    public static final String LOG_TAG = "thingengine.Service";

    private final EngineService ctx;
    private final Handler handler;
    private final BluetoothAdapter adapter;
    private final Map<String, BluetoothDevice> pairing = new HashMap<>();
    private final Map<String, BluetoothDevice> fetchingUuids = new HashMap<>();

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
                case BluetoothDevice.ACTION_UUID:
                    onDeviceChanged((BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                    return;
            }
        }
    }

    public BluetoothAPI(Handler handler, EngineService ctx, ControlChannel control) {
        super("Bluetooth", control);

        this.ctx = ctx;
        this.handler = handler;
        adapter = ((BluetoothManager)ctx.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        discovering = false;

        registerAsync("start", new GenericCall() {
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

        registerAsync("startDiscovery", new GenericCall() {
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

        registerAsync("pairDevice", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                pairDevice((String)args[0]);
                return null;
            }
        });

        registerAsync("readUUIDs", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return readUUIDs((String)args[0]);
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
        ctx.registerReceiver(receiver, filter, null, handler);

        if (adapter != null) {
            Collection<BluetoothDevice> devices = adapter.getBondedDevices();
            for (BluetoothDevice d : devices)
                onDeviceFound(d);
        }
    }

    private void stop() {
        ctx.unregisterReceiver(receiver);
        receiver = null;
    }

    private void onStateChanged(int newState, int oldState) {
        invokeAsync("onstatechanged", new int[] { newState, oldState });

        if (discovering && newState == BluetoothAdapter.STATE_ON && adapter != null)
            adapter.startDiscovery();
    }

    private void onDiscoveryFinished() {
        invokeAsync("ondiscoveryfinished", null);
    }

    @NonNull
    private JSONArray uuidsToJson(@Nullable ParcelUuid[] uuids) {
        if (uuids == null)
            return new JSONArray();

        JSONArray ret = new JSONArray();
        for (ParcelUuid uuid : uuids)
            ret.put(uuid.getUuid().toString());
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

        synchronized (this) {
            boolean shouldNotify = false;
            String hwAddress = device.getAddress().toLowerCase();
            if (pairing.containsKey(hwAddress)) {
                pairing.put(hwAddress, device);
                shouldNotify = true;
            }
            if (fetchingUuids.containsKey(hwAddress)) {
                fetchingUuids.put(hwAddress, device);
                shouldNotify = true;
            }
            if (shouldNotify)
                notifyAll();
        }
    }

    private void startDiscovery(long timeout) throws InterruptedException {
        if (adapter == null)
            throw new UnsupportedOperationException("This device has no Bluetooth adapter");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopDiscovery();
            }
        }, timeout);

        if (adapter.startDiscovery()) {
            discovering = true;
            return;
        }

        InteractionCallback callback = ctx.getInteractionCallback();
        if (callback == null)
            throw new UnsupportedOperationException("Bluetooth is disabled and operation is in background");

        if (!callback.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), InteractionCallback.ENABLE_BLUETOOTH))
            throw new InterruptedException("User denied enabling Bluetooth");

        if (adapter.startDiscovery()) {
            discovering = true;
        }
    }

    private void stopDiscovery() {
        discovering = false;
        if (adapter != null)
            adapter.cancelDiscovery();
    }

    private void pairDevice(String address) throws InterruptedException {
        if (adapter == null)
            throw new UnsupportedOperationException("This device has no Bluetooth adapter");

        BluetoothDevice dev = adapter.getRemoteDevice(address.toUpperCase());

        if (dev.getBondState() == BluetoothDevice.BOND_BONDED)
            return;

        synchronized (this) {
            try {
                while (true) {
                    if (!pairing.containsKey(address)) {
                        pairing.put(address, dev);
                        dev.createBond();
                    } else {
                        dev = pairing.get(address);
                        if (dev.getBondState() == BluetoothDevice.BOND_BONDED)
                            return;
                        if (dev.getBondState() != BluetoothDevice.BOND_BONDING)
                            throw new InterruptedException("User cancelled bonding");
                    }

                    wait();
                }
            } finally {
                pairing.remove(address);
            }
        }
    }

    private final long FETCH_UUID_TIMEOUT = 20000;

    @NonNull
    private JSONArray readUUIDs(String address) throws InterruptedException {
        if (adapter == null)
            throw new UnsupportedOperationException("This device has no Bluetooth adapter");

        BluetoothDevice dev = adapter.getRemoteDevice(address.toUpperCase());
        long startTime = System.currentTimeMillis();
        synchronized (this) {
            try {
                while (true) {
                    if (!fetchingUuids.containsKey(address)) {
                        fetchingUuids.put(address, dev);
                        if (!dev.fetchUuidsWithSdp())
                            throw new RuntimeException("Fetching UUIDs failed with a generic error");
                    } else {
                        dev = fetchingUuids.get(address);
                        ParcelUuid[] uuids = dev.getUuids();
                        if (uuids != null && uuids.length > 0)
                            return uuidsToJson(uuids);
                    }

                    long now = System.currentTimeMillis();
                    if (FETCH_UUID_TIMEOUT - (now - startTime) < 1)
                        throw new InterruptedException("Fetching UUIDs timed out");
                    wait(FETCH_UUID_TIMEOUT - (now - startTime));
                    now = System.currentTimeMillis();
                    if (now - startTime > FETCH_UUID_TIMEOUT)
                        throw new InterruptedException("Fetching UUIDs timed out");
                }
            } finally {
                fetchingUuids.remove(address);
            }
        }
    }
}
