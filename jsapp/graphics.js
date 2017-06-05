// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2016 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const JavaAPI = require('./java_api');
const ImageAPI = JavaAPI.makeJavaAPI('Image',
    ['createImage', 'resizeFit'],
    ['imageToBuffer', 'imageToStream', 'imageGetWidth', 'imageGetHeight', 'imageDispose'],
    []);

const StreamAPI = require('./streams');

class Image {
    constructor(how, data) {
        this._promise = ImageAPI.createImage(how, data);
    }

    getSize() {
        return this._promise.then((token) => {
            var width = ImageAPI.imageGetWidth(token);
            var height = ImageAPI.imageGetHeight(token);
            ImageAPI.imageDispose(token);
            return { width: width, height: height };
        });
    }

    resizeFit(width, height) {
        this._promise = this._promise.then((token) =>
            ImageAPI.resizeFit(token, width, height).then(() => token));
    }

    toBuffer(format) {
        return this._promise.then((token) =>
            ImageAPI.imageToBuffer(token, format || 'png'));
    }

    stream(format) {
        return this._promise
        .then((token) => ImageAPI.imageToStream(token, format || 'png'))
        .then((streamToken) => StreamAPI.get().createStream(streamToken));
    }
}

module.exports = {
    createImageFromPath(path) {
        return new Image('path', path);
    },

    createImageFromBuffer(buffer) {
        return new Image('buffer', buffer.toString('base64'));
    },
};

