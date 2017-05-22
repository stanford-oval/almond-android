// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// Copyright 2017 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

process._eval = null;
var launcher = process._linkedBinding('android-launcher');

var callbacks = new Map;
launcher.setAsyncReceiver(function(fn, args) {
    if (!callbacks.has(fn))
        throw new TypeError('No such callback ' + fn);
    try {
        return callbacks.get(fn).apply(null, args);
    } catch(e) {
        console.error('Callback ' + fn + ' threw', e);
        throw e;
    }
});

var util = require('util');
console.log = function(...args) {
    return launcher.log(util.format.apply(null, args) + '\n');
}
console.error = function(...args) {
    return launcher.error(util.format.apply(null, args) + '\n');
}
console.warn = function(...args) {
    return launcher.warn(util.format.apply(null, args) + '\n');
}
process.reallyExit = launcher.exit;

module.exports = {
    registerCallback(fn, callback) {
        callbacks.set(fn, callback);
    },
    unregisterCallback(fn) {
        callbacks.delete(fn);
    },
    getCallback(fn) {
        return callbacks.get(fn);
    },

    callJavaSync: launcher.callJavaSync,
    callJavaAsync: launcher.callJavaAsync
}
