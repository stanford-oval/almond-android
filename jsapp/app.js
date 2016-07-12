// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

console.log('ThingEngine-Android starting up...');

// we need these very early on
const Q = require('q');
const JavaAPI = require('./java_api');
const ControlChannel = require('./control');

var _engine;
var _waitReady;
var _running;
var _stopped;

class AppControlChannel extends ControlChannel {
    // handle control methods here...

    invokeCallback(callbackId, error, value) {
        JavaAPI.invokeCallback(callbackId, error, value);
    }

    stop() {
        if (_running)
            _engine.stop();
        else
            _stopped = true;
        this.close();
    }

    startOAuth2(kind) {
        return _engine.devices.factory.runOAuth2(kind, null);
    }

    handleOAuth2Callback(kind, req) {
        return _engine.devices.factory.runOAuth2(kind, req).then(() => {
            return true;
        });
    }

    createDevice(state) {
        return _engine.devices.loadOneDevice(state, true).then(() => {
            return true;
        });
    }

    deleteDevice(uniqueId) {
        var device = _engine.devices.getDevice(uniqueId);
        if (device === undefined)
            return false;

        _engine.devices.removeDevice(device);
        return true;
    }

    getDeviceInfos() {
        return _waitReady.then(function() {
            var devices = _engine.devices.getAllDevices();

            return devices.map(function(d) {
                return { uniqueId: d.uniqueId,
                         name: d.name || "Unknown device",
                         description: d.description || "Description not available",
                         kind: d.kind,
                         ownerTier: d.ownerTier,
                         isTransient: d.isTransient,
                         isOnlineAccount: d.hasKind('online-account'),
                         isDataSource: d.hasKind('data-source'),
                         isThingEngine: d.hasKind('thingengine-system') };
            });
        }, function(e) {
            return [];
        });
    }

    getDeviceInfo(uniqueId) {
        return _waitReady.then(function() {
            var d = _engine.devices.getDevice(uniqueId);
            if (d === undefined)
                throw new Error('Invalid device ' + uniqueId);

            return { uniqueId: d.uniqueId,
                     name: d.name || "Unknown device",
                     description: d.description || "Description not available",
                     kind: d.kind,
                     ownerTier: d.ownerTier,
                     isTransient: d.isTransient,
                     isOnlineAccount: d.hasKind('online-account'),
                     isDataSource: d.hasKind('data-source'),
                     isThingEngine: d.hasKind('thingengine-system') }
        });
    }

    checkDeviceAvailable(uniqueId) {
        return _waitReady.then(function() {
            var d = _engine.devices.getDevice(uniqueId);
            if (d === undefined)
                return -1;

            return d.checkAvailable();
        });
    }

    setCloudId(cloudId, authToken) {
        if (_engine.devices.hasDevice('thingengine-own-cloud'))
            return false;
        if (!platform.setAuthToken(authToken))
            return false;

        _engine.devices.loadOneDevice({ kind: 'org.thingpedia.builtin.thingengine',
                                        tier: 'cloid',
                                        cloudId: cloudId,
                                        own: true }, true).done();
        return true;
    }

    setServerAddress(serverHost, serverPort, authToken) {
        if (_engine.devices.hasDevice('thingengine-own-server'))
            return false;
        if (authToken !== null) {
            if (!platform.setAuthToken(authToken))
                return false;
        }

        _engine.devices.loadOneDevice({ kind: 'org.thingpedia.builtin.thingengine',
                                        tier: 'server',
                                        host: serverHost,
                                        port: serverPort,
                                        own: true }, true).done();
        return true;
    }
}

function runEngine() {
    Q.longStackSupport = true;

    // we would like to create the control channel without
    // initializing the platform but we can't because the
    // control channels needs paths and encodings from the platform
    global.platform = require('./platform');
    platform.init().then(function() {
        console.log('Android platform initialized');

        // create the control channel immediately so we free
        // the UI thread to go on merrily on it's own
        var controlChannel = new AppControlChannel();

        return controlChannel.open();
    }).then(function() {
        // signal to unblock the UI thread
        // we don't need to async-wait for the result here, the call is sync
        // and execute on our thread
        JXMobile('controlReady').callNative();

        console.log('Control channel ready');

        // we would like to load this first, but it's huge
        // so we delay until after we have the control channel
        require('babel-polyfill');

        // finally load the bulk of the code and create the engine
        const Engine = require('thingengine-core');
        const AssistantDispatcher = require('./assistant');

        console.log('Creating engine...');
        _engine = new Engine(global.platform);

        var ad = new AssistantDispatcher(_engine);

        _waitReady = _engine.open();
        ad.start();
        return _waitReady;
    }).then(function() {
        _running = true;
        if (_stopped)
            return;
        return _engine.run();
    }).finally(function() {
        ad.stop();
        return _engine.close();
    }).catch(function(error) {
        console.log('Uncaught exception: ' + error.message);
        console.log(error.stack);
    }).finally(function() {
        console.log('Cleaning up');
        platform.exit();
    }).done();
}

JXMobile('runEngine').registerToNative(runEngine);

