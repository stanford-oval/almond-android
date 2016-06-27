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

const JavaAPI = require('../java_api');
const AssistantJavaApi = JavaAPI.makeJavaAPI('Assistant', [],
    ['send', 'sendPicture', 'sendRDL', 'sendChoice', 'sendLink', 'sendButton'],
    ['onhandlecommand', 'onhandleparsedcommand', 'onhandlepicture', 'onassistantready']);

var instance_;

module.exports = class AssistantDispatcher {
    constructor(engine) {
        this.engine = engine;

        this._sempre = new SempreClient();
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
        AssistantJavaApi.onassistantready = this._onAssistantReady.bind(this);
    }

    stop() {
        this._sempre.stop();

        AssistantJavaApi.onhandlecommand = null;
        AssistantJavaApi.onhandleparsedcommand = null;
        AssistantJavaApi.onhandlepicture = null;
        AssistantJavaApi.onassistantready = null;
    }

    getConversation() {
        this._ensureConversation
        return this._conversation;
    }

    _onAssistantReady() {
        this._ensureConversation();
    }

    _ensureConversation() {
        if (this._conversation !== null)
            return;

        this._conversation = new Conversation(this.engine, this);
        this._conversation.start();
    }

    _onHandleParsedCommand(error, json) {
        this._ensureConversation();

        this._conversation.handleCommand(null, json).catch(function(e) {
            console.log('Failed to handle assistant command: ' + e.message);
        }).done();
    }

    _onHandleCommand(error, text) {
        this._ensureConversation();

        this.analyze(text).then(function(analyzed) {
            this._conversation.handleCommand(text, analyzed);
        }.bind(this)).catch(function(e) {
            console.log('Failed to handle assistant command: ' + e.message);
        }).done();
    }

    _onHandlePicture(error, url) {
        this._ensureConversation();

        this._conversation.handlePicture(url).catch(function(e) {
            console.log('Failed to handle assistant picture: ' + e.message);
        }).done();
    }

    analyze(what) {
        return this._session.sendUtterance(what);
    }

    send(what) {
        AssistantJavaApi.send(what).done();
    }

    sendPicture(url) {
        AssistantJavaApi.sendPicture(url).done()
    }

    sendRDL(rdl) {
        AssistantJavaApi.sendRDL(JSON.stringify(rdl)).done();
    }

    sendChoice(idx, what, title, text) {
        AssistantJavaApi.sendChoice(idx, what, title, text).done();
    }

    sendLink(title, url) {
        AssistantJavaApi.sendLink(title, url).done();
    }
};
