// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2015 The Board of Trustees of the Leland Stanford Junior University
//
// Author: Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

const stream = require('stream');
const Tp = require('thingpedia');

// The phone running this instance of ThingEngine, and its
// phone specific channels (like sms and popup notifications)
module.exports = class ThingEnginePhoneDevice extends Tp.BaseDevice {
    constructor(engine, state) {
        super(engine, state);

        // This is a built-in device so we're allowed some
        // "friendly" API access
        this._tierManager = engine._tiers;

        this.uniqueId = 'org.thingpedia.builtin.thingengine.phone';

        this.name = this.engine._("Phone");
        this.description = this.engine._("Access your phone capabilities from Almond.");
    }

    get ownerTier() {
        return Tp.Tier.PHONE;
    }

    checkAvailable() {
        if (Tp.Tier.PHONE === this._tierManager.ownTier) {
            return Tp.Availability.AVAILABLE;
        } else {
            return (this._tierManager.isConnected(Tp.Tier.PHONE) ?
                    Tp.Availability.AVAILABLE :
                    Tp.Availability.OWNER_UNAVAILABLE);
        }
    }

    // FIXME sms
    get_sms() {
        throw new Error(`Receiving SMS is not supported in this version of Almond.`);
        return [];
    }
    subscribe_sms() {
        throw new Error(`Receiving SMS is not supported in this version of Almond.`);
        
        let sms = this.engine.platform.getCapability('sms');

        let smsstream = new stream.Readable({ objectMode: true, read() {} });
        sms.onsmsreceived = (error, sms) => {
            if (error)
                smsstream.emit('error', error);
            else
                smsstream.push({ from: sms.from, body: sms.body });
        };
        smsstream.destroy = () => sms.stop();
        sms.start();
        return smsstream;
    }

    do_call(args) {
        const telephone = this.engine.platform.getCapability('telephone');
        return telephone.call(String(args.number));
    }
    do_call_emergency() {
        const telephone = this.engine.platform.getCapability('telephone');
        return telephone.callEmergency();
    }
    do_notify(args) {
        const notify = this.engine.platform.getCapability('notify');
        return notify.showMessage(args.title, args.message);
    }
    do_set_ringer(args) {
        const audio = this.engine.platform.getCapability('audio-manager');
        return audio.setRingerMode(args.mode);
    }
    do_send_sms(args) {
        throw new Error(`Sending SMS is not supported in this version of Almond.`);

        const sms = this.engine.platform.getCapability('sms');
        return sms.sendMessage(String(args.to), args.body);
    }
};
