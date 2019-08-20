// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 The Board of Trustees of the Leland Stanford Junior University
//
// Author: Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";
// must be before anything to finish setting up nodejs-android
require('./launcher');

console.log('ThingEngine-Android starting up...');

// we need these very early on
const Q = require('q');
Q.longStackSupport = true;

const ControlChannel = require('./control');
const Config = require('./config');

var _engine, _ad;
var _waitReady;
var _running;
var _stopped;

class AppControlChannel extends ControlChannel {
    constructor(platform) {
        super();
        this._platform = platform;
    }

    // handle control methods here...
    stop() {
        if (_running)
            _engine.stop();
        else
            _stopped = true;
    }

    startOAuth2(kind) {
        return _engine.startOAuth(kind);
    }

    handleOAuth2Callback(kind, requestUri, session) {
        return _engine.completeOAuth(kind, requestUri, session).then(() => true);
    }

    createDevice(state) {
        return _engine.createDevice(state).then(() => true);
    }

    deleteDevice(uniqueId) {
        return _engine.deleteDevice(uniqueId);
    }

    upgradeDevice(kind) {
        console.log('upgradeDevice', kind);
        return _engine.upgradeDevice(kind).then(() => true);
    }

    getDeviceInfos() {
        return _waitReady.then(() => {
            return _engine.getDeviceInfos();
        });
    }

    getDeviceInfo(uniqueId) {
        return _waitReady.then(() => {
            return _engine.getDeviceInfo(uniqueId);
        });
    }

    checkDeviceAvailable(uniqueId) {
        return _waitReady.then(() => {
            return _engine.checkDeviceAvailable(uniqueId);
        });
    }

    getAppInfos() {
        return _waitReady.then(() => {
            return _engine.getAppInfos();
        });
    }

    deleteApp(uniqueId) {
        return _waitReady.then(() => {
            return _engine.deleteApp(uniqueId);
        });
    }

    setCloudId(cloudId, authToken) {
        return _engine.setCloudId(cloudId, authToken);
    }

    setServerAddress(serverHost, serverPort, authToken) {
        return _engine.addServerAddress(serverHost, serverPort, authToken);
    }

    getAllPermissions() {
        return _engine.getAllPermissions();
    }

    revokePermission(uniqueId) {
        return _engine.revokePermission(uniqueId);
    }
}

async function main() {
    // HACK: run a timer during initialization to keep the mainloop running
    let counter = 0;
    let timer = setInterval(() => {
        if (counter >= 600)
            clearInterval(timer);
        counter++;
    }, 100);

    try {
        global.platform = require('./platform');
        global.platform.init();
        new AppControlChannel(global.platform);

        console.log('Android platform initialized');

        // load the bulk of the code and create the engine
        const Engine = require('thingengine-core');
        const AssistantDispatcher = require('./assistant');

        console.log('Creating engine...');
        _engine = new Engine(global.platform, { thingpediaUrl: Config.THINGPEDIA_URL });

        _ad = new AssistantDispatcher(_engine);
        global.platform.setAssistant(_ad);

        console.log('Opening engine...');

        try {
            _waitReady = _engine.open();
            _ad.start();
            
            try {
                await _waitReady;
                _ad.engineReady();
            } catch(error) {
                console.error('Early exception: ' + error.message);
                console.error(error.stack);
                _ad.earlyError(error);
            }
            if (!_stopped) {
                _running = true;
                await _engine.run();
            }
        } finally {
            _ad.stop();
            await _engine.close();
        }
    } catch (error) {
        console.error('Uncaught exception: ' + error.message);
        console.error(error.stack);
    } finally {
        console.log('Cleaning up');
        //platform.exit();
    }
}

main();
