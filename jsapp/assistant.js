// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2016 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Config = require('./config');

const Sabrina = require('sabrina');

const JavaAPI = require('./java_api');

const COMMANDS = ['send', 'sendPicture', 'sendChoice', 'sendLink', 'sendButton', 'sendAskSpecial'];
const AssistantJavaApi = JavaAPI.makeJavaAPI('Assistant', [],
    COMMANDS.concat(['sendRDL']),
    ['onhandlecommand', 'onhandleparsedcommand']);

class LocalUser {
    constructor() {
        this.id = 0;
        this.account = 'INVALID';
        this.name = platform.getSharedPreferences().get('user-name');
    }
}

class AssistantDispatcher {
    constructor(engine) {
        this._conversation = new Sabrina(engine, new LocalUser(), this, true, Config.SEMPRE_URL);
    }

    start() {
        AssistantJavaApi.onhandlecommand = this._onHandleCommand.bind(this);
        AssistantJavaApi.onhandleparsedcommand = this._onHandleParsedCommand.bind(this);
        this._conversation.start();
    }

    stop() {
        AssistantJavaApi.onhandlecommand = null;
        AssistantJavaApi.onhandleparsedcommand = null;
    }

    getConversation() {
        return this._conversation;
    }

    _onHandleParsedCommand(error, json) {
        return this._conversation.handleParsedCommand(json);
    }

    _onHandleCommand(error, text) {
        return this._conversation.handleCommand(text);
    }

    // sendRDL is special because we need to stringify the rdl before we
    // call the Java API, or jxcore will marshal it weirdly
    sendRDL(rdl) {
        return AssistantJavaApi.sendRDL(JSON.stringify(rdl));
    }
};
COMMANDS.forEach(function(c) {
    AssistantDispatcher.prototype[c] = AssistantJavaApi[c];
});

module.exports = AssistantDispatcher;
