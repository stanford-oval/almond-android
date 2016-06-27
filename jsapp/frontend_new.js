// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Q = require('q');

module.exports = class Frontend {
    constructor(engine) {
        this._engine = engine;
    }

    startOAuth2(kind) {
        return this._engine.devices.factory.runOAuth2(kind, null);
    }

    handleOAuth2Callback(kind, req) {
        return this._engine.devices.factory.runOAuth2(kind, req).then(() => {
            return true;
        });
    }
}
