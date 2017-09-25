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

const COMMANDS = ['send', 'sendPicture', 'sendChoice', 'sendLink', 'sendButton', 'sendAskSpecial', 'sendBrassau', 'sendRDL'];
const AssistantJavaApi = JavaAPI.makeJavaAPI('Assistant', [],
    COMMANDS,
    ['onready', 'onhandlecommand', 'onhandleparsedcommand', 'onbrassauready']);

class LocalUser {
    constructor(platform) {
        this.id = 0;
        this.account = 'INVALID';
        this.name = platform.getSharedPreferences().get('user-name');
    }
}

class AssistantDispatcher {
    constructor(engine, api) {
        this._engine = engine;
        this._conversation = null;
        this._api = api;
        this._api.setAssistant(this);

        this._engineReady = false;
        this._uiReady = false;
        this._apiReady = false;
    }

    start() {
        AssistantJavaApi.onhandlecommand = this._onHandleCommand.bind(this);
        AssistantJavaApi.onhandleparsedcommand = this._onHandleParsedCommand.bind(this);
        AssistantJavaApi.onready = this._onUIReady.bind(this);
        AssistantJavaApi.onbrassauready = this._onBrassauReady.bind(this);
    }

    stop() {
        AssistantJavaApi.onhandlecommand = null;
        AssistantJavaApi.onhandleparsedcommand = null;
        AssistantJavaApi.onready = null;
        AssistantJavaApi.onbrassauready = null;
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
        this._conversation.notify(...data);
        this._api.notify(...data);
    }

    notifyErrorAll(...data) {
        this._ensureConversation();
        this._conversation.notifyError(...data);
        this._api.notifyError(...data);
    }

    getConversation(id) {
        if (id === 'api')
            return this._api;

        this._ensureConversation();
        return this._conversation;
    }

    _onBrassauReady() {
        this._apiReady = true;
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
}
COMMANDS.forEach((c) => {
    AssistantDispatcher.prototype[c] = AssistantJavaApi[c];
});

module.exports = AssistantDispatcher;
