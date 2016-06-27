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

const ThingTalk = require('thingtalk');
const AppGrammar = ThingTalk.Grammar;

function getAllDevices(engine) {
    var devices = engine.devices.getAllDevices();

    return devices.map(function(d) {
        return { uniqueId: d.uniqueId,
                 name: d.name || "Unknown device",
                 description: d.description || "Description not available",
                 kind: d.kind,
                 ownerTier: d.ownerTier,
                 available: d.available,
                 isTransient: d.isTransient,
                 isOnlineAccount: d.hasKind('online-account'),
                 isDataSource: d.hasKind('data-source'),
                 isThingEngine: d.hasKind('thingengine-system') };
    }).filter(function(d) {
        return !d.isThingEngine;
    });
}

router.get('/', function(req, res) {
    Q().then(function(appinfo) {
        var devinfo = getAllDevices(req.app.engine);

        var physical = [], online = [], datasource = [];
        devinfo.forEach(function(d) {
            if (d.isDataSource)
                datasource.push(d);
            else if (d.isOnlineAccount)
                online.push(d);
            else
                physical.push(d);
        });
        res.render('my_stuff', { page_title: 'ThingEngine - Dashboard',
                                 datasourceDevices: datasource,
                                 physicalDevices: physical,
                                 onlineDevices: online,
                                });
    }).done();
});

module.exports = router;
