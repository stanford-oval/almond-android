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

const feeds = require('../../shared/util/feeds');

const ThingTalk = require('thingtalk');
const AppGrammar = ThingTalk.Grammar;

function getAllApps(engine) {
    var apps = engine.apps.getAllApps();

    return Q.all(apps.map(function(a) {
        return Q.try(function() {
            if (state.$F) {
                return engine.messaging.getFeedMeta(state.$F).then(function(f) {
                    return feeds.getFeedName(engine, f, true);
                });
            } else {
                return null;
            }
        }).then(function(feed) {
            var app = { uniqueId: a.uniqueId, name: a.name || "Some app",
                        running: a.isRunning, enabled: a.isEnabled,
                        state: a.state, error: a.error, feed: feed };
            return app;
        });
    }));
}

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
    var shareApps = req.flash('share-apps');
    var sharedApp = null;

    getAllApps(req.app.engine).then(function(appinfo) {
        var devinfo = getAllDevices(req.app.engine);

        if (shareApps.length > 0) {
            appinfo.forEach(function(app) {
                if (shareApps[0] === app.uniqueId)
                    sharedApp = app;
            });
        }

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
                                 messages: req.flash('app-message'),
                                 sharedApp: sharedApp,
                                 csrfToken: req.csrfToken(),
                                 apps: appinfo,
                                 datasourceDevices: datasource,
                                 physicalDevices: physical,
                                 onlineDevices: online,
                                });
    }).done();
});

function appsCreate(error, req, res) {
    return feeds.getFeedList(req.app.engine, false).then(function(feeds) {
        res.render('apps_create', { page_title: 'ThingEngine - create app',
                                    csrfToken: req.csrfToken(),
                                    error: error,
                                    code: req.body.code,
                                    parameters: req.body.params || '{}',
                                    omlet: { feeds: feeds,
                                             feedId: req.body.feedId }
                                  });
    });
}

router.get('/create', function(req, res, next) {
    appsCreate(undefined, req, res).catch(function(e) {
        res.status(400).render('error', { page_title: "ThingEngine - Error",
                                          message: e.message });
    }).done();
});

router.post('/create', function(req, res, next) {
    var compiler;
    var code = req.body.code;
    var name = req.body.name;
    var description = req.body.description;
    var state;
    var ast;

    Q.try(function() {
        var engine = req.app.engine;

        // sanity check the app
        ast = AppGrammar.parse(code);
        var state = JSON.parse(req.body.params);
        if (ast.name.feedAccess) {
            if (!state.$F && !req.body.feedId)
                throw new Error('Missing feed for feed-shared app');
            if (!state.$F)
                state.$F = req.body.feedId;
        } else {
            delete state.$F;
        }

        return engine.apps.loadOneApp(code, state, null, undefined,
                                      name, description, true);
    }).then(function() {
        if (ast.name.feedAccess && !req.query.shared) {
            req.flash('app-message', "Application successfully created");
            req.flash('share-apps', 'app-' + ast.name.name + state.$F.replace(/[^a-zA-Z0-9]+/g, '-'));
            res.redirect(303, '/apps');
        } else {
            req.flash('app-message', "Application successfully created");
            res.redirect(303, '/apps');
        }
    }).catch(function(e) {
        res.status(400).render('error', { page_title: "ThingPedia - Error",
                                          message: e });
    }).done();
});

router.post('/delete', function(req, res, next) {
    Q.try(function() {
        var engine = req.app.engine;

        var id = req.body.id;
        var app = engine.apps.getApp(id);
        if (app === undefined) {
            res.status(404).render('error', { page_title: "ThingEngine - Error",
                                              message: "Not found." });
            return;
        }

        return engine.apps.removeApp(app).then(function() {
            req.flash('app-message', "Application successfully deleted");
            res.redirect(303, '/apps');
        });
    }).catch(function(e) {
        res.status(400).render('error', { page_title: "ThingEngine - Error",
                                          message: e.message + '\n' + e.stack });
    }).doe();
});

router.get('/:id/results', function(req, res, next) {
    var engine = req.app.engine;

    var app = engine.apps.getApp(req.params.id);
    if (app === undefined) {
        res.status(404).render('error', { page_title: "ThingPedia - Error",
                                          message: "Not found." });
        return;
    }

    var name = app.name;
    Q(app.pollOutVariables()).then(function(esults) {
        // FIXME do something smarter with feedAccessible keywords
        // and complex types

        var arrays = [];
        var tuples = [];
        var singles = [];
        results.forEach(function(r) {
            if (Array.isArray(r.value)) {
                if (r.type.startsWith('(') && !r.feedAccess)
                    tuples.push(r);
                else
                    arrays.push(r);
            } else {
                singles.push(r);
            }
        });
        return res.render('show_app_results', { page_title: "ThingPedia App",
                                                appId: req.params.id,
                                                name: name,
                                                arrays: arrays,
                                                tuples: tuples,
                                                singles: singles });
    }).catch(function(e) {
        console.log(e.stack);
        res.status(400).render('error', { page_title: "ThingPedia - Error",
                                          message: e });
    }).done();
});

router.post('/:id/update', function(req, res, next) {
    var engine = req.app.engine;

    var app = engine.apps.getApp(req.params.id);
    if (app === undefined) {
        res.status(404).render('error', { page_title: "ThingEngine - Error",
                                          message: "Not found." });
        return;
    }

    // do something
    Q.try(function() {
        var code = req.body.code;
        var state;
        return Q.try(function() {
            // sanity check the app
            var compiler = new AppCompiler();
            compiler.setSchemaRetriever(engine.schemas);
            return compiler.compileCode(code).then(function() {
                state = JSON.parse(req.body.params);
            });
        }).then(function() {
            return engine.apps.loadOneApp(code, state, req.params.id, undefined, null, null, true).then(function() {
                appsList(req, res, next, "Application successfully updated");
            });
        }).catch(function(e) {
            res.render('show_app', { page_title: 'ThingEngine App',
                                     name: app.name,
                                     description: app.description || '',
                                     csrfToken: req.csrfToken(),
                                     error: e.message,
                                     code: code,
                                     params: req.body.params });
            return;
        });
    }).catch(function(e) {
        res.status(400).render('error', { page_title: "ThingEngine - Error",
                                          message: e.message });
    }).done();
});

module.exports = router;
