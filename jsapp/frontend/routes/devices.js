// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const Q = require('q');

const express = require('express');
var router = express.Router();

router.get('/create', function(req, res, next) {
    if (req.query.class && ['online', 'physical', 'data'].indexOf(req.query.class) < 0) {
        res.status(404).render('error', { page_title: "ThingPedia - Error",
                                          message: "Invalid device class" });
        return;
    }

    res.render('devices_create', { page_title: 'ThingEngine - configure device',
                                   csrfToken: req.csrfToken(),
                                   developerKey: platform.getDeveloperKey(),
                                   klass: req.query.class,
                                   ownTier: 'phone',
                                 });
});

router.post('/create', function(req, res, next) {
    var engine = req.app.engine;
    var devices = engine.devices;

    Q.try(function() {
        if (typeof req.body['kind'] !== 'string' ||
            req.body['kind'].length == 0)
            throw new Error("You must choose one kind of device");

        delete req.body['_csrf'];

        return devices.loadOneDevice(req.body, true);
    }).then(function() {
        if (req.session['device-redirect-to']) {
            res.redirect(303, req.session['device-redirect-to']);
            delete req.session['device-redirect-to'];
        } else {
            res.redirect(303, '/apps');
        }
    }).catch(function(e) {
        res.status(400).render('error', { page_title: "ThingEngine - Error",
                                          message: e.message });
    }).done();
});

router.get('/create/:kind', function(req, res, next) {
    var engine = req.app.engine;
    var devices = engine.devices;

    Q.try(function() {
        var devices = engine.devices;

        return devices.loadOneDevice({ kind: req.params.kind }, true);
    }).then(function() {
        if (req.session['device-redirect-to']) {
            res.redirect(303, req.session['device-redirect-to']);
            delete req.session['device-redirect-to'];
        } else {
            res.redirect(303, '/apps');
        }
    }).catch(function(e) {
        res.status(400).render('error', { page_title: "ThingEngine - Error",
                                          message: e.message });
    }).done();
});

router.post('/delete', function(req, res, next) {
    if (req.query.class && ['online', 'physical'].indexOf(req.query.class) < 0) {
        res.status(404).render('error', { page_title: "ThingEngine - Error",
                                          message: "Invalid device class" });
        return;
    }

    var engine = req.app.engine;
    var id = req.body.id;
    var device;
    try {
        if (!engine.devices.hasDevice(id))
            device = undefined;
        else
            device = engine.devices.getDevice(id);

        if (device === undefined) {
            res.status(404).render('error', { page_title: "ThingEngine - Error",
                                              message: "Not found." });
            return;
        }

        engine.devices.removeDevice(device);
        if (req.session['device-redirect-to']) {
            res.redirect(303, req.session['device-redirect-to']);
            delete req.session['device-redirect-to'];
        } else {
            res.redirect(303, '/apps');
        }
    } catch(e) {
        res.status(400).render('error', { page_title: "ThingEngine - Error",
                                          message: e.message });
    }
});


module.exports = router;
