// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Launcher = require('./launcher');

module.exports = class ControlChannel {
    constructor() {
        var self = this;
        var calls = Object.getOwnPropertyNames(Object.getPrototypeOf(this));
        calls.forEach((call) => {
            Launcher.registerCallback('Control_' + call, function() {
                var r = self[call].apply(self, arguments);
                // make sure that all promises are native v8 promises, because the c++ code
                // relies on that
                if (typeof r === 'object' && r !== null && typeof r.then === 'function')
                    return Promise.resolve(r).catch((e) => {
                        console.error('Error in async control call ' + call, e);
                        throw e;
                    });
                else
                    return r;
            });
        });
    }
}

