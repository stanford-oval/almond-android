// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2016 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const stream = require('stream');

const JavaAPI = require('./java_api');
const StreamAPI = JavaAPI.makeJavaAPI([], [], ['onstreamdata', 'onstreamerror', 'onstreamend']);

var _instance;

module.exports = class Streams {
    constructor() {
        _instance = this;

        this._streams = {};

        StreamAPI.onstreamdata = this._onStreamData.bind(this);
        StreamAPI.onstreamerror = this._onStreamError.bind(this);
        StreamAPI.onstreamend = this._onStreamEnd.bind(this);
    }

    static get() {
        return _instance;
    }

    createStream(token) {
        var readable = new stream.Readable({ read: function() {} });
        this._streams[token] = readable;
        return readable;
    }

    _onStreamError(unused, arg) {
        var token = arg[0];
        var error = arg[1];

        var readable = this._streams[token];
        if (!readable)
            return;
        delete this._streams[token];
        readable.emit('error', new Error(error));
    }

    _onStreamData(unused, arg) {
        var token = arg[0];
        var data = arg[1];

        var readable = this._streams[token];
        if (!readable)
            return;
        readable.push(new Buffer(arg, 'base64'));
    }

    _onStreamEnd(unused, token) {
        var readable = this._streams[token];
        if (!readable)
            return;
        delete this._streams[token];
        readable.push(null);
    }
}
