// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.jsapi;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.thingengine.engine.service.EngineService;

/**
 * Created by gcampagn on 7/7/16.
 */
public class AudioRouterAPI extends JavascriptAPI {
    private final Context ctx;
    private final Handler handler;
    private final AudioManager audioManager;
    private final BluetoothManager btManager;
    private final BluetoothAdapter btAdapter;

    private AtomicInteger refCount = new AtomicInteger(0);

    private BluetoothA2dp ad2p;
    private synchronized void onProxyAcquired(BluetoothA2dp proxy) {
        ad2p = proxy;
        notifyAll();
    }
    private synchronized void onProxyLost() {
        ad2p = null;
        notifyAll();
    }

    private final BluetoothProfile.ServiceListener ad2pServiceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.A2DP)
                return;
            onProxyAcquired((BluetoothA2dp)proxy);
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile != BluetoothProfile.A2DP)
                return;
            onProxyLost();
        }
    };

    public AudioRouterAPI(Handler handler, Context ctx) {
        super("AudioRouter");

        this.handler = handler;
        this.ctx = ctx;
        audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        btManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        registerSync("start", new Runnable() {
            @Override
            public void run() {
                start();
            }
        });

        registerSync("stop", new Runnable() {
            @Override
            public void run() {
                stop();
            }
        });

        registerAsync("setAudioRouteBluetooth", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                setAudioRouteBluetooth((String) args[0]);
                return null;
            }
        });

        registerSync("isAudioRouteBluetooth", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return isAudioRouteBluetooth((String) args[0]);
            }
        });
    }

    private void start() {
        if (refCount.incrementAndGet() == 1)
            btAdapter.getProfileProxy(ctx, ad2pServiceListener, BluetoothProfile.A2DP);
    }

    private void stop() {
        if (refCount.decrementAndGet() > 0)
            return;

        BluetoothA2dp oldA2dp;
        synchronized (this) {
            oldA2dp = ad2p;
            ad2p = null;
            notifyAll();
        }
        btAdapter.closeProfileProxy(BluetoothProfile.A2DP, oldA2dp);
    }

    private void setAudioRouteBluetooth(String address) throws InterruptedException {
        if (btAdapter == null)
            throw new UnsupportedOperationException("This device has no bluetooth adapter");

        BluetoothDevice btDevice = btAdapter.getRemoteDevice(address.toUpperCase());

        start();
        try {
            BluetoothA2dp currentAd2p;
            synchronized (this) {
                currentAd2p = ad2p;
                while (currentAd2p == null) {
                    wait(5000); // wait at most 5 seconds
                    currentAd2p = ad2p;
                }
            }

            try {
                Method connect = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
                connect.invoke(currentAd2p, btDevice);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Unable to switch AD2P profile to given device, Android SDK changed incompatibly", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }

            audioManager.setSpeakerphoneOn(true);
            audioManager.setBluetoothA2dpOn(true);
        } finally {
            stop();
        }
    }

    private boolean isAudioRouteBluetooth(String address) throws InterruptedException {
        if (btAdapter == null)
            return false;
        if (!audioManager.isBluetoothA2dpOn())
            return false;

        BluetoothDevice btDevice = btAdapter.getRemoteDevice(address.toUpperCase());
        start();
        try {
            BluetoothA2dp currentAd2p;
            synchronized (this) {
                currentAd2p = ad2p;
                while (currentAd2p == null) {
                    wait(5000); // wait at most 5 seconds
                    currentAd2p = ad2p;
                }
            }
            try {
                boolean ret = currentAd2p.isA2dpPlaying(btDevice);
                Log.i(EngineService.LOG_TAG, "AudioRouter.isAudioRouteBluetooth returned " + ret);
                return ret;
            } catch (Throwable t) {
                Log.e(EngineService.LOG_TAG, "Unexpected error in isA2dpPlaying", t);
                return false;
            }
        } finally {
            stop();
        }
    }
}
