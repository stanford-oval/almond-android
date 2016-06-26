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

const SempreClient = require('sabrina').Sempre;

const Conversation = require('./conversation');
const LinkedQueue = require('./linkedqueue');

const JavaAPI = require('../java_api');
const AssistantJavaApi = JavaAPI.makeJavaAPI('Assistant', [],
    ['send', 'sendPicture', 'sendRDL', 'sendChoice', 'sendLink', 'sendButton'],
    ['onhandlecommand', 'onhandleparsedcommand', 'onhandlepicture', 'onassistantresume']);

var instance_;

module.exports = class AssistantDispatcher {
    constructor(engine) {
        this.engine = engine;

        this._sempre = new SempreClient();
        this._history = new LinkedQueue();
        this._conversation = null;
        this._socket = null;

        instance_ = this;
    }

    static get() {
        return instance_;
    }

    start() {
        this._sempre.start();
        this._session = this._sempre.openSession();

        AssistantJavaApi.onhandlecommand = this._onHandleCommand.bind(this);
        AssistantJavaApi.onhandleparsedcommand = this._onHandleParsedCommand.bind(this);
        AssistantJavaApi.onhandlepicture = this._onHandlePicture.bind(this);
        AssistantJavaApi.onassistantresume = this._onAssistantResume.bind(this);
    }

    stop() {
        this._sempre.stop();

        AssistantJavaApi.onhandlecommand = null;
        AssistantJavaApi.onhandleparsedcommand = null;
        AssistantJavaApi.onhandlepicture = null;
        AssistantJavaApi.onassistantresume = null;
    }

    getConversation() {
        this._ensureConversation
        return this._conversation;
    }

    _replayHistory(item) {
        var call = item[0];
        var args = item[1];

        AssistantJavaApi[call].apply(AssistantJavaApi, args).done();
    }

    _onAssistantResume() {
        for (var msg of this._history)
            this._replayHistory(item);

        this._ensureConversation();
    }

    _ensureConversation() {
        if (this._conversation !== null)
            return;

        this._conversation = new Conversation(this.engine, this);
        this._conversation.start();
    }

    _onHandleParsedCommand(error, json) {
        this._conversation.handleCommand(null, json).catch(function(e) {
            console.log('Failed to handle assistant command: ' + e.message);
        }).done();
    }

    _onHandleCommand(error, text) {
        // FIXME: record message from user

        this.analyze(text).then(function(analyzed) {
            this._conversation.handleCommand(text, analyzed);
        }.bind(this)).catch(function(e) {
            console.log('Failed to handle assistant command: ' + e.message);
        }).done();
    }

    _onHandlePicture(error, url) {
        this._conversation.handlePicture(url).catch(function(e) {
            console.log('Failed to handle assistant picture: ' + e.message);
        }).done();
    }

    analyze(what) {
        return this._session.sendUtterance(what);
    }

    _queue(call, args) {
        this._history.push([call, args]);

        AssistantJavaApi[call].apply(AssistantJavaApi, args).done();
    }

    send(what) {
        this._queue('send', arguments);
    }

    sendPicture(url) {
        this._queue('sendPicture', arguments);
    }

    sendRDL(rdl) {
        this._queue('sendRDL', [JSON.stringify(rdl)]);
    }

    sendChoice(idx, what, title, text) {
        this._queue('sendChoice', arguments);
    }

    sendLink(title, url) {
        this._queue('sendLink', arguments);
    }
};
