// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Q = require('q');
const AssistantDispatcher = require('../../assistant/dispatcher');

module.exports = function(app) {
    app.get('/assistant', function(req, res) {
        res.render('assistant', { page_title: "ThingEngine - Sabrina" });
    });

    app.ws('/assistant/socket', function(ws, req) {
        console.log('Client connected on websocket');
        AssistantDispatcher.get().setSocket(ws);
    });
};
