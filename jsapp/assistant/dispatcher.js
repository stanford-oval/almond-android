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
    }

    stop() {
        this._sempre.stop();
    }

    setSocket(ws) {
        if (this._socket !== null)
            this._socket.close();

        this._socket = ws;
        ws.on('close', () => {
            if (this._socket === ws) {
                this._socket = null;
            }
        });

        ws.on('message', (msg) => this._handleMessage(msg));
        //FIXME
        //for (var msg of this._history)
        //    ws.send(JSON.stringify(msg));

        this._ensureConversation();
    }

    _ensureConversation() {
        if (this._conversation !== null)
            return;

        this._conversation = new Conversation(this.engine, this);
        this._conversation.start();

        if (this._conversation === null)
            console.log('lul wut');
    }

    _onHiddenMessage(text) {
        try {
            var parsed = JSON.parse(text);
        } catch(e) {
            console.log('Failed to parse hidden message as JSON: ' + e.message);
            return;
        }

        this._conversation.handleCommand(null, text).catch(function(e) {
            console.log('Failed to handle assistant command: ' + e.message);
        }).done();
    }

    _onTextMessage(text) {
        console.log('this._conversation before', this._conversation);
        this.analyze(text).then(function(analyzed) {
            console.log('this._conversation after', this._conversation);
            this._conversation.handleCommand(text, analyzed);
        }.bind(this)).catch(function(e) {
            console.log('Failed to handle assistant command: ' + e.message);
        }).done();
    }

    _onPicture(url) {
        this._conversation.handlePicture(url).catch(function(e) {
            console.log('Failed to handle assistant picture: ' + e.message);
        }).done();
    }

    _handleMessage(data) {
        console.log('Received data on WebSocket', data);

        var msg = JSON.parse(data);
        if (msg.type === 'text') {
            if (msg.hidden)
                this._onHiddenMessage(msg.text);
            else
                this._onTextMessage(msg.text);
        } else if (msg.type === 'picture') {
            this._onPicture(msg.url);
        }
    }

    analyze(what) {
        return this._session.sendUtterance(what);
    }

    _queue(msg) {
        this._history.push(msg);
        if (this._socket)
            this._socket.send(JSON.stringify(msg));
    }

    send(what) {
        this._queue({ 'text': what });
    }

    sendPicture(url) {
        this._queue({ 'picture': url });
    }

    sendRDL(rdl) {
        this._queue({ 'rdl': rdl });
    }

    sendChoice(idx, what, title, text) {
        this._queue({ 'button': { id: idx, title: title, text: text } });
    }
};
