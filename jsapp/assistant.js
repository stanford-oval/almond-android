// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2016 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Config = require('./config');

const Almond = require('almond');

const JavaAPI = require('./java_api');

const COMMANDS = ['send', 'sendPicture', 'sendChoice', 'sendLink', 'sendButton', 'sendAskSpecial'];
const AssistantJavaApi = JavaAPI.makeJavaAPI('Assistant', [],
    COMMANDS.concat(['sendRDL']),
    ['onready', 'onhandlecommand', 'onhandleparsedcommand']);

class LocalUser {
    constructor(platform) {
        this.id = 0;
        this.account = 'INVALID';
        this.name = platform.getSharedPreferences().get('user-name');
    }
}

class AssistantDispatcher {
    constructor(engine) {
        this._engine = engine;
        this._conversation = null;

        this._engineReady = false;
        this._uiReady = false;
    }

    start() {
        AssistantJavaApi.onhandlecommand = this._onHandleCommand.bind(this);
        AssistantJavaApi.onhandleparsedcommand = this._onHandleParsedCommand.bind(this);
        AssistantJavaApi.onready = this._onUIReady.bind(this);
    }

    stop() {
        AssistantJavaApi.onhandlecommand = null;
        AssistantJavaApi.onhandleparsedcommand = null;
        AssistantJavaApi.onready = null;
    }

    _ensureConversation() {
        if (this._conversation)
            return;
        this._conversation = new Almond(this._engine, 'native-android', new LocalUser(this._engine.platform), this, {
            debug: true,
            sempreUrl: Config.SEMPRE_URL,
            showWelcome: true
        });
        this._conversation.start();
    }

    notifyAll(...data) {
        this._ensureConversation();
        return this._conversation.notify(...data);
    }

    notifyErrorAll(...data) {
        this._ensureConversation();
        return this._conversation.notifyError(...data);
    }

    getConversation() {
        this._ensureConversation();
        return this._conversation;
    }

    _onUIReady() {
        this._uiReady = true;
        if (this._engineReady && this._uiReady)
            this._ensureConversation();
    }

    engineReady() {
        this._engineReady = true;
        if (this._engineReady && this._uiReady)
            this._ensureConversation();
    }
    earlyError(error) {
        AssistantJavaApi.send('Sorry, I failed to start: ' + error.message);
        AssistantJavaApi.send('Please restart the app and try again');
    }

    _onHandleParsedCommand(error, json) {
        this._ensureConversation();
        return this._conversation.handleParsedCommand(json);
    }

    _onHandleCommand(error, text) {
        this._ensureConversation();
        return this._conversation.handleCommand(text);
    }

    // sendRDL is special because we need to stringify the rdl before we
    // call the Java API, or jxcore will marshal it weirdly
    sendRDL(rdl, icon) {
        return AssistantJavaApi.sendRDL(JSON.stringify(rdl), icon);
    }
}
COMMANDS.forEach((c) => {
    AssistantDispatcher.prototype[c] = AssistantJavaApi[c];
});

module.exports = AssistantDispatcher;
