// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2017 The Board of Trustees of the Leland Stanford Junior University
//
// Author: Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const gettextParser = require('gettext-parser');
const fs = require('fs');

var translation = gettextParser.po.parse(fs.readFileSync(process.argv[2]), 'utf-8');
console.log(JSON.stringify(translation));
