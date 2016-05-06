// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

console.log('ThingEngine-Android starting up...');

const Q = require('q');
const fs = require('fs');

const ControlChannel = require('./control');
const Engine = require('thingengine-core');
const Tier = require('thingpedia').Tier;

const JavaAPI = require('./java_api');

var _engine;
var _running;
var _stopped;

class AppControlChannel extends ControlChannel {
    // handle control methods here...

    foo(int) {
        console.log('Foo called on control channel with value ' + int);
        return int;
    }

    invokeCallback(callbackId, error, value) {
        JavaAPI.invokeCallback(callbackId, error, value);
    }

    stop() {
        if (_running)
            _engine.stop();
        else
            _stop = true;
        this.close();
    }

    setCloudId(cloudId, authToken) {
        if (_engine.devices.hasDevice('thingengine-own-cloud'))
            return false;
        if (!platform.setAuthToken(authToken))
            return false;

        _engine.devices.loadOneDevice({ kind: 'thingengine',
                                        tier: Tier.CLOUD,
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

        _engine.devices.loadOneDevice({ kind: 'thingengine',
                                        tier: Tier.SERVER,
                                        host: serverHost,
                                        port: serverPort,
                                        own: true }, true).done();
        return true;
    }
}

function runEngine() {
    Q.longStackSupport = true;

    global.platform = require('./platform');

    platform.init().then(function() {
        console.log('Android platform initialized');
        console.log('Creating engine...');

        _engine = new Engine(global.platform);
        var controlChannel = new AppControlChannel();

        return controlChannel.open().then(function() {
            // signal early to stop the engine
            // we don't need to async-wait for the result here, the call is sync
            // and execute on our thread
            JXMobile('controlReady').callNative();

            return _engine.open();
        }).then(function() {
            _running = true;
            if (_stopped)
                return _engine.close();
            return _engine.run().finally(function() {
                return _engine.close();
            });
        });
    }).catch(function(error) {
        console.log('Uncaught exception: ' + error.message);
        console.log(error.stack);
    }).finally(function() {
        console.log('Cleaning up');
        platform.exit();
    }).done();
}

console.log('Registering to JXMobile');
JXMobile('runEngine').registerToNative(runEngine);

