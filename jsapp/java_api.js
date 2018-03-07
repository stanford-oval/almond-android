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

const Q = require('q');
const Launcher = require('./launcher');

module.exports.makeJavaAPI = function makeJavaAPI(klass, asyncMethods, syncMethods, events) {
    var obj = {};
    asyncMethods.forEach((method) => {
        var call = klass + '_' + method;
        Object.defineProperty(obj, method, {
            configurable: true,
            enumerable: false,
            writable: true,
            value: function(...args) {
                // for compatibility, wrap the native promise into a Q
                // promise
                return Q(Launcher.callJavaAsync(call, ...args));
            }
        });
    });
    syncMethods.forEach((method) => {
        var call = klass + '_' + method;
        Object.defineProperty(obj, method, {
            configurable: true,
            enumerable: false,
            writable: true,
            value: function(...args) {
                return Launcher.callJavaSync(call, ...args);
            }
        });
    });
    events.forEach((event) => {
        Object.defineProperty(obj, event, {
            configurable: true,
            enumerable: false,
            get() {
                return Launcher.getCallback(klass + '_' + event) || null;
            },
            set(callback) {
                if (callback !== null) {
                    Launcher.registerCallback(klass + '_' + event, function() {
                        var r = callback.apply(obj, arguments);
                        // make sure that all promises are native v8 promises, because the c++ code
                        // relies on that
                        if (typeof r === 'object' && r !== null && typeof r.then === 'function')
                            return Promise.resolve(r);
                        else
                            return r;
                    });
                } else {
                    Launcher.unregisterCallback(klass + '_' + event);
                }
            }
        });
    });

    return obj;
};
