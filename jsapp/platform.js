// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

// Android platform

const fs = require('fs');
const Module = require('module');
const Gettext = require('node-gettext');

const JavaAPI = require('./java_api');
const StreamAPI = require('./streams');
const CVC4Solver = require('cvc4');

const _platformApi = JavaAPI.makeJavaAPI('Platform', [],
    ['getFilesDir', 'getCacheDir', 'getLocale', 'getTimezone'], []);
const _unzipApi = JavaAPI.makeJavaAPI('Unzip', ['unzip'],
    [], []);
const _gpsApi = JavaAPI.makeJavaAPI('Gps', ['start', 'stop', 'getCurrentLocation'],
    [], ['onlocationchanged']);
const _notifyApi = JavaAPI.makeJavaAPI('Notify', [],
    ['showMessage'], []);
const _audioManagerApi = JavaAPI.makeJavaAPI('AudioManager', [],
    ['setRingerMode', 'adjustMediaVolume', 'setMediaVolume'], []);
const _smsApi = JavaAPI.makeJavaAPI('Sms', ['start', 'stop', 'sendMessage'],
    [], ['onsmsreceived']);
const _btApi = JavaAPI.makeJavaAPI('Bluetooth',
    ['start', 'startDiscovery', 'pairDevice', 'readUUIDs'],
    ['stop', 'stopDiscovery'],
    ['ondeviceadded', 'ondevicechanged', 'onstatechanged', 'ondiscoveryfinished']);
const _audioRouterApi = JavaAPI.makeJavaAPI('AudioRouter',
    ['setAudioRouteBluetooth', 'isAudioRouteBluetooth'],
    ['start', 'stop'], []);
const _systemAppsApi = JavaAPI.makeJavaAPI('SystemApps', [],
    ['startMusic'], []);
const _graphicsApi = require('./graphics');

const _contentJavaApi = JavaAPI.makeJavaAPI('Content', ['getStream'],
    [], []);
const _contentApi = {
    getStream(url) {
        return _contentJavaApi.getStream(url).then((obj) =>
            StreamAPI.get().createStream(obj.token, obj.contentType));
    }
};
const _contactApi = JavaAPI.makeJavaAPI('Contacts', ['lookup', 'lookupPrincipal'],
    [], []);
const _telephoneApi = JavaAPI.makeJavaAPI('Telephone', ['call', 'callEmergency'],
    [], []);
const _sharedPrefsApi = JavaAPI.makeJavaAPI('SharedPreferences', [],
    ['readSharedPref', 'writeSharedPref'], []);

class AndroidSharedPreferences {
    constructor() {
        this._writes = [];
        this._scheduledWrite = false;
    }

    _flushWrites() {
        if (this._writes.length === 0)
            return;

        var writes = this._writes;
        this._writes = [];
        // can't pass complex objects to Java, so go through JSON
        try {
            _sharedPrefsApi.writeSharedPref(JSON.stringify(writes));
        } catch(error) {
            console.error('Failed to flush shared preferences to disk');
        }
    }

    get(name) {
        this._flushWrites();

        var _value = _sharedPrefsApi.readSharedPref(name);
        if (_value === null)
            return undefined;
        return JSON.parse(_value);
    }

    set(name, value) {
        this._writes.push([name, JSON.stringify(value)]);

        if (this._scheduledWrite)
            return value;

        this._scheduledWrite = true;
        setTimeout(() => {
            this._flushWrites();
            this._scheduledWrite = false;
        }, 30000);

        return value;
    }
}

var filesDir = null;
var cacheDir = null;

function safeMkdirSync(dir) {
    try {
        fs.mkdirSync(dir);
    } catch(e) {
        if (e.code !== 'EEXIST')
            throw e;
    }
}

var _prefs;

// patch Module to know about some of our browserified modules
var oldModuleLoad = Module._load;
Module._load = function(request, parent, isMain) {
    if (request === 'thingpedia')
        return require('thingpedia');
    if (request === 'thingtalk')
        return require('thingtalk');
    // for compat with twitter...
    if (request === 'thingpedia/lib/ref_counted')
        return require('thingpedia/lib/ref_counted');
    return oldModuleLoad.apply(this, arguments);
};

// "preload" locales
var _locales = {
    zh_CN: {
        almond: require('almond/po/zh'),
        'thingengine-core': require('thingengine-core/po/zh_CN'),
    },
    it: {
        almond: require('almond/po/it'),
        'thingengine-core': require('thingengine-core/po/it')
    }
};

module.exports = {
    // Initialize the platform code
    // Will be called before instantiating the engine
    init: function() {
        this._assistant = null;

        this._gettext = new Gettext();

        new StreamAPI();

        filesDir = _platformApi.getFilesDir();
        cacheDir = _platformApi.getCacheDir();
        safeMkdirSync(cacheDir);
        safeMkdirSync(cacheDir + '/tmp');
        this._locale = _platformApi.getLocale();
        this._timezone = _platformApi.getTimezone();
        _prefs = new AndroidSharedPreferences();

        var locale = this._locale.split(/[-_\.@]/);
        var attempt = locale.join('_');
        while (!_locales[attempt] && locale.length > 0) {
            locale.pop();
            attempt = locale.join('_');
        }
        if (locale.length === 0)
            return;
        locale = _locales[attempt];
        for (var domain in locale)
            this._gettext.addTranslations(this._locale, domain, locale[domain]);
        // free the memory associated with the locales we don't need
        _locales = null;

        this._gettext.setLocale(this._locale);
    },

    setAssistant(ad) {
        this._assistant = ad;
    },

    type: 'android',

    get encoding() {
        return 'utf16be';
    },

    get locale() {
        return this._locale;
    },

    get timezone() {
        return this._timezone;
    },

    // Check if we need to load and run the given thingengine-module on
    // this platform
    // (eg we don't need discovery on the cloud, and we don't need graphdb,
    // messaging or the apps on the phone client)
    hasFeature: function(feature) {
        switch(feature) {
        case 'graphdb':
            return false;

        default:
            return true;
        }
    },

    // Check if this platform has the required capability
    // (eg. long running, big storage, reliable connectivity, server
    // connectivity, stable IP, local device discovery, bluetooth, etc.)
    //
    // Which capabilities are available affects which apps are allowed to run
    hasCapability: function(cap) {
        switch(cap) {
        case 'code-download':
            // If downloading code from the thingpedia server is allowed on
            // this platform
            return true;

        case 'android-api':
            // We can use the Android APIs if we need to
            return true;

        // We can use the phone capabilities
        case 'notify':
        case 'gps':
        case 'audio-manager':
        case 'sms':
        case 'bluetooth':
        case 'audio-router':
        case 'system-apps':
        case 'graphics-api':
        case 'content-api':
        case 'contacts':
        case 'telephone':
        // for compat
        case 'notify-api':
            return true;

        case 'smt-solver':
            return true;

        case 'assistant':
            return true;

        case 'gettext':
            return true;

        default:
            return false;
        }
    },

    // Retrieve an interface to an optional functionality provided by the
    // platform
    //
    // This will return null if hasCapability(cap) is false
    getCapability: function(cap) {
        switch(cap) {
        case 'notify-api':
        case 'notify':
            return _notifyApi;

        case 'gps':
            return _gpsApi;

        case 'audio-manager':
            return _audioManagerApi;

        case 'sms':
            return _smsApi;

        case 'code-download':
            // We have the support to download code
            return _unzipApi;

        case 'bluetooth':
            return _btApi;

        case 'audio-router':
            return _audioRouterApi;

        case 'system-apps':
            return _systemAppsApi;

        case 'graphics-api':
            return _graphicsApi;

        case 'content-api':
            return _contentApi;

        case 'contacts':
            return _contactApi;

        case 'telephone':
            return _telephoneApi;

        case 'assistant':
            return this._assistant;

        case 'gettext':
            return this._gettext;

        case 'smt-solver':
            // FIXME
            // return CVC4Solver;
            return HttpSmtSolver;

        default:
            return null;
        }
    },

    // Obtain a shared preference store
    // Preferences are simple key/value store which is shared across all apps
    // but private to this instance (tier) of the platform
    // Preferences should be normally used only by the engine code, and a persistent
    // shared store such as DataVault should be used by regular apps
    getSharedPreferences: function() {
        return _prefs;
    },

    // Get the root of the application
    // (In android, this is the virtual root of the APK)
    getRoot: function() {
        return process.cwd();
    },

    // Get a directory that is guaranteed to be writable
    // (in the private data space for Android)
    getWritableDir: function() {
        return filesDir;
    },

    // Get a temporary directory
    // Also guaranteed to be writable, but not guaranteed
    // to persist across reboots or for long times
    // (ie, it could be periodically cleaned by the system)
    getTmpDir: function() {
        return cacheDir + '/tmp';
    },

    // Get a directory good for long term caching of code
    // and metadata
    getCacheDir: function() {
        return cacheDir;
    },

    // Make a symlink potentially to a file that does not exist physically
    makeVirtualSymlink: function(file, link) {
        // FIXME
    },

    // Get the filename of the sqlite database
    getSqliteDB: function() {
        return filesDir + '/sqlite.db';
    },

    // For now, the version of sqlite compiled with jxcore does not support
    // on-disk encryption, so no key returned here
    getSqliteKey: function() {
        return null;
    },

    getGraphDB: function() {
        return filesDir + '/rdf.db';
    },

    // Stop the main loop and exit
    // (In Android, this only stops the node.js thread)
    // This function should be called by the platform integration
    // code, after stopping the engine
    exit: function() {
        return process.exit();
    },

    // Get the ThingPedia developer key, if one is configured
    getDeveloperKey: function() {
        return _prefs.get('developer-key');
    },

    // Change the ThingPedia developer key, if possible
    // Returns true if the change actually happened
    setDeveloperKey: function(key) {
        _prefs.set('developer-key', key);
        return true;
    },

    // Return a server/port URL that can be used to refer to this
    // installation. This is primarily used for OAuth redirects, and
    // so must match what the upstream services accept.
    getOrigin: function() {
        return 'http://127.0.0.1:3000';
    },

    getCloudId() {
        return _prefs.get('cloud-id');
    },

    getAuthToken() {
        return _prefs.get('auth-token');
    },

    // Change the auth token
    // Returns true if a change actually occurred, false if the change
    // was rejected
    setAuthToken: function(authToken) {
        var oldAuthToken = _prefs.get('auth-token');
        if (oldAuthToken !== undefined && authToken !== oldAuthToken)
            return false;
        _prefs.set('auth-token', authToken);
        return true;
    },

    // For internal use only
    _getPrivateFeature: function() {
        throw new Error('No private features on Android (yet)');
    },
};
