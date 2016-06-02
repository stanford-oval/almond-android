// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const express = require('express');
var router = express.Router();

router.get('/', function(req, res, next) {
    res.render('index', { page_title: "ThingEngine - run your things!" });
});

module.exports = router;
