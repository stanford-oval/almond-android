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
        return _engine.devices.factory.runOAuth2(kind, null);
    }

    handleOAuth2Callback(kind, req) {
        return _engine.devices.factory.runOAuth2(kind, req).then(() => true);
    }

    createDevice(state) {
        return _engine.devices.loadOneDevice(state, true).then(() => true);
    }

    deleteDevice(uniqueId) {
        var device = _engine.devices.getDevice(uniqueId);
        if (device === undefined)
            return false;

        _engine.devices.removeDevice(device);
        return true;
    }

    upgradeDevice(kind) {
        console.log('upgradeDevice', kind);
        return _engine.devices.updateDevicesOfKind(kind).then(() => true);
    }

    getDeviceInfos() {
        return _waitReady.then(() => {
            var devices = _engine.devices.getAllDevices();

            return devices.map((d) => {
                return { uniqueId: d.uniqueId,
                         name: d.name || "Unknown device",
                         description: d.description || "Description not available",
                         kind: d.kind,
                         ownerTier: d.ownerTier,
                         version: d.constructor.metadata.version || 0,
                         isTransient: d.isTransient,
                         isOnlineAccount: d.hasKind('online-account'),
                         isDataSource: d.hasKind('data-source'),
                         isThingEngine: d.hasKind('thingengine-system') };
            });
        }, () => []);
    }

    getDeviceInfo(uniqueId) {
        return _waitReady.then(() => {
            var d = _engine.devices.getDevice(uniqueId);
            if (d === undefined)
                throw new Error('Invalid device ' + uniqueId);

            return { uniqueId: d.uniqueId,
                     name: d.name || "Unknown device",
                     description: d.description || "Description not available",
                     kind: d.kind,
                     ownerTier: d.ownerTier,
                     version: d.constructor.metadata.version || 0,
                     isTransient: d.isTransient,
                     isOnlineAccount: d.hasKind('online-account'),
                     isDataSource: d.hasKind('data-source'),
                     isThingEngine: d.hasKind('thingengine-system') };
        });
    }

    checkDeviceAvailable(uniqueId) {
        return _waitReady.then(() => {
            var d = _engine.devices.getDevice(uniqueId);
            if (d === undefined)
                return -1;

            return d.checkAvailable();
        });
    }

    getAppInfos() {
        return _waitReady.then(() => {
            var apps = _engine.apps.getAllApps();

            return apps.map((a) => {
                var app = { uniqueId: a.uniqueId,
                            name: a.name || "Some app",
                            description: a.description || a.name || "Some app",
                            icon: a.icon || null,
                            isRunning: a.isRunning,
                            isEnabled: a.isEnabled,
                            error: a.error };
                return app;
            });
        });
    }

    deleteApp(uniqueId) {
        return _waitReady.then(() => {
            var app = _engine.apps.getApp(uniqueId);
            if (app === undefined)
                return false;

            return _engine.apps.removeApp(app).then(() => true);
        });
    }

    setCloudId(cloudId, authToken) {
        if (_engine.devices.hasDevice('thingengine-own-cloud'))
            return false;
        if (!this._platform.setAuthToken(authToken))
            return false;

        // we used to call loadOneDevice() with thingengine kind, tier: cloud here
        // but is incompatible with syncing the developer key (and causes
        // spurious device database writes)
        // instead we set the platform state and reopen the connection
        this._platform.getSharedPreferences().set('cloud-id', cloudId);
        _engine.tiers.reopenOne('cloud').done();
        return true;
    }

    setServerAddress(serverHost, serverPort, authToken) {
        if (_engine.devices.hasDevice('thingengine-own-server'))
            return false;
        if (authToken !== null) {
            if (!this._platform.setAuthToken(authToken))
                return false;
        }

        _engine.devices.loadOneDevice({ kind: 'org.thingpedia.builtin.thingengine',
                                        tier: 'server',
                                        host: serverHost,
                                        port: serverPort,
                                        own: true }, true).done();
        return true;
    }

    getAllPermissions() {
        return _engine.permissions.getAllPermissions().map((p) => ({
            uniqueId: p.uniqueId,
            description: p.description
        }));
    }

    revokePermission(uniqueId) {
        return _engine.permissions.removePermission(uniqueId);
    }
}

function main() {
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

    _waitReady = _engine.open();
    _ad.start();
    _waitReady.then(() => {
        _ad.engineReady();
        _running = true;
        if (_stopped)
            return Q();
        return _engine.run();
    }, (error) => {
        console.error('Early exception: ' + error.message);
        console.error(error.stack);
        _ad.earlyError(error);
    }).catch((error) => {
        console.error('Uncaught exception: ' + error.message);
        console.error(error.stack);
    }).finally(() => {
        _ad.stop();
        return _engine.close();
    }).catch((error) => {
        console.error('Exception during stop: ' + error.message);
        console.error(error.stack);
    }).finally(() => {
        console.log('Cleaning up');
        //platform.exit();
    }).done();
}

main();
