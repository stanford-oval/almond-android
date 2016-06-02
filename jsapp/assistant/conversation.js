// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2016 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Q = require('q');
const events = require('events');
const util = require('util');

const Sabrina = require('sabrina').Sabrina;

class LocalUser {
    constructor() {
        this.id = 0;
        this.account = 'INVALID';
        this.name = platform.getSharedPreferences().get('user-name');
    }
}

module.exports = class Assistant extends Sabrina {
    constructor(engine, delegate) {
        super(engine, new LocalUser(), delegate, true);

        this._engine = engine;
        this._notify = null;
        this._notifyListener = this.notify.bind(this);
    }

    getAllNotify() {
        return this.engine.channels.getNamedPipe('thingengine-app-notify', 'r');
    }

    start() {
        super.start();

        return this.getAllNotify().then(function(notify) {
            this._notify = notify;
            notify.on('data', this._notifyListener);
        }.bind(this));
    }

    stop() {
        if (this._notify) {
            this._notify.removeListener('data', this._notifyListener);
            return this._notify.close();
        } else {
            return Q();
        }
    }
}
