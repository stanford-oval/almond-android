// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details

// Android platform

const Q = require('q');
const fs = require('fs');
const Gettext = require('node-gettext');

const JavaAPI = require('./java_api');
const StreamAPI = require('./streams');

const _unzipApi = JavaAPI.makeJavaAPI('Unzip', ['unzip'], [], []);
const _gpsApi = JavaAPI.makeJavaAPI('Gps', ['start', 'stop'], [], ['onlocationchanged']);
const _notifyApi = JavaAPI.makeJavaAPI('Notify', [], ['showMessage'], []);
const _audioManagerApi = JavaAPI.makeJavaAPI('AudioManager', [],
    ['setRingerMode', 'adjustMediaVolume', 'setMediaVolume'], []);
const _smsApi = JavaAPI.makeJavaAPI('Sms', ['start', 'stop', 'sendMessage'], [], ['onsmsreceived']);
const _btApi = JavaAPI.makeJavaAPI('Bluetooth',
    ['start', 'startDiscovery', 'pairDevice', 'readUUIDs'],
    ['stop', 'stopDiscovery'],
    ['ondeviceadded', 'ondevicechanged', 'onstatechanged', 'ondiscoveryfinished']);
const _audioRouterApi = JavaAPI.makeJavaAPI('AudioRouter',
    ['setAudioRouteBluetooth'], ['start', 'stop', 'isAudioRouteBluetooth'], []);
const _systemAppsApi = JavaAPI.makeJavaAPI('SystemApps', [], ['startMusic'], []);
const _graphicsApi = require('./graphics');

const _contentJavaApi = JavaAPI.makeJavaAPI('Content', ['getStream'], [], []);
const _contentApi = {
    getStream(url) {
        return _contentJavaApi.getStream(url).then(function(obj) {
            return StreamAPI.get().createStream(obj.token, obj.contentType);
        });
    }
}
const _contactApi = JavaAPI.makeJavaAPI('Contacts', ['lookup'], [], []);
const _telephoneApi = JavaAPI.makeJavaAPI('Telephone', ['call', 'callEmergency'], [], []);

var filesDir = null;
var cacheDir = null;
var encoding = null;

function safeMkdirSync(dir) {
    try {
        fs.mkdirSync(dir);
    } catch(e) {
        if (e.code != 'EEXIST')
            throw e;
    }
}

var _prefs = null;

// monkey patch fs to support virtual symlinks, which we need to get
// symlinks to js files in the apk
var _originalRealpathSync = fs.realpathSync;
var _originalStatSync = fs.statSync;
var _originalLStatSync = fs.lstatSync;
var _virtualSymlinks = {};
var _virtualSymlinksStat = {};
fs.statSync = function(p) {
    for (var symlink in _virtualSymlinks) {
        if (p.startsWith(symlink + '/')) {
            var relative = p.substr(symlink.length);
            //console.log('found', _virtualSymlinks[symlink] + relative);
            return fs.statSync(_virtualSymlinks[symlink] + relative);
        }
    }

    return _originalStatSync(p);
};
fs.lstatSync = function(p) {
    for (var symlink in _virtualSymlinks) {
        if (p === symlink) {
            if (_virtualSymlinksStat.hasOwnProperty(p))
                return _virtualSymlinksStat[p];

            var stats = new fs.JXStats(0, 41471); // 0777 | S_IFLNK
            stats.isSymbolicLink = function() {
                return true;
            }
            return _virtualSymlinksStat[p] = stats;
        } else if (p.startsWith(symlink + '/')) {
            return fs.statSync(p);
        }
    }

    return _originalLStatSync(p);
};
fs.realpathSync = function(p, cache) {
    for (var symlink in _virtualSymlinks) {
        if (p.startsWith(symlink + '/')) {
            var relative = p.substr(symlink.length);
            return fs.realpathSync(_virtualSymlinks[symlink] + relative, cache);
        }
    }

    return _originalRealpathSync(p, cache);
};

module.exports = {
    // Initialize the platform code
    // Will be called before instantiating the engine
    init: function() {
        this._assistant = null;

        this._gettext = new Gettext();

        new StreamAPI();

        return Q.nfcall(JXMobile.GetDocumentsPath).then((dir) => {
            filesDir = dir;
            safeMkdirSync(filesDir + '/tmp');
            return Q.nfcall(JXMobile.GetEncoding);
        }).then((value) => {
            encoding = value;
            return Q.nfcall(JXMobile.GetLocale);
        }).then((value) => {
            this._locale = value;
            this._gettext.setlocale(value);
            return Q.nfcall(JXMobile.GetTimezone);
        }).then((value) => {
            this._timezone = value;
            return Q.nfcall(JXMobile.GetSharedPreferences);
        }).then((prefs) => {
            _prefs = prefs;
            return Q.nfcall(JXMobile.GetCachePath);
        }).then((value) => {
            cacheDir = value;
            safeMkdirSync(cacheDir);
        });
    },

    setAssistant(ad) {
        this._assistant = ad;
    },

    type: 'android',

    get encoding() {
        return encoding;
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
        case 'ui':
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
            return this._assistant.getConversation();

        case 'gettext':
            return this._gettext;

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
        return filesDir + '/tmp';
    },

    // Get a directory good for long term caching of code
    // and metadata
    getCacheDir: function() {
        return cacheDir;
    },

    // Make a symlink potentially to a file that does not exist physically
    makeVirtualSymlink: function(file, link) {
        _virtualSymlinks[link] = file;
        // fix some brokenness in Android 6
        // not sure what's going on but half of the code sees paths
        // with /data/data and the other half with /data/user/0
        link = link.replace(/\/data\/user\/[0-9]+\//, "/data/data/");
        _virtualSymlinks[link] = file;
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
        return JXMobile.Exit();
    },

    // Get the ThingPedia developer key, if one is configured
    getDeveloperKey: function() {
        return _prefs.get('developer-key');
    },

    // Change the ThingPedia developer key, if possible
    // Returns true if the change actually happened
    setDeveloperKey: function(key) {
        return _prefs.set('developer-key', key);
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
