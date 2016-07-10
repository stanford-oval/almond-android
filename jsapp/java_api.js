// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details

const Q = require('q');

var asyncCallbacks = {};
var eventCallbacks = {};

module.exports.makeJavaAPI = function makeJavaAPI(klass, asyncMethods, syncMethods, events) {
    var obj = {};
    asyncMethods.forEach(function(method) {
        Object.defineProperty(obj, method, {
            configurable: true,
            enumerable: false,
            writable: true,
            value: function() {
                var call = JXMobile(klass + '_' + method);
                var cb = call.callAsyncNative.apply(call, arguments);
                var defer = Q.defer();
                asyncCallbacks[cb] = defer;
                return defer.promise;
            }
        });
    });
    syncMethods.forEach(function(method) {
        Object.defineProperty(obj, method, {
            configurable: true,
            enumerable: false,
            writable: true,
            value: function() {
                var call = JXMobile(klass + '_' + method);
                return Q.npost(call, 'callNative', arguments).catch((e) => {
                    if (typeof e === 'string')
                        throw new Error(e);
                    else
                        throw e;
                });
            }
        });
    });
    events.forEach(function(event) {
        eventCallbacks[klass + '_' + event] = undefined;
        Object.defineProperty(obj, event, {
            configurable: true,
            enumerable: false,
            get() {
                return eventCallbacks[klass + '_' + callbackName];
            },
            set(callback) {
                if (callback !== null)
                    eventCallbacks[klass + '_' + event] = callback;
                else
                    eventCallbacks[klass + '_' + event] = undefined;
            }
        });
    });

    return obj;
}

module.exports.invokeCallback = function(callbackId, error, value) {
    if (callbackId in eventCallbacks) {
        if (eventCallbacks[callbackId] === undefined) {
            // ignore event with no listeners
            return;
        }
        return eventCallbacks[callbackId](error, value);
    }

    if (!callbackId in asyncCallbacks) {
        console.log('Invalid callback ID ' + callbackId);
        return;
    }

    if (error)
        asyncCallbacks[callbackId].reject(new Error(error));
    else
        asyncCallbacks[callbackId].resolve(value);
    delete asyncCallbacks[callbackId];
};
