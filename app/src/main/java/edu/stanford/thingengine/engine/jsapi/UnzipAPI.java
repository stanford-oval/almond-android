// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.jsapi;

/**
 * Created by gcampagn on 12/1/15.
 */
public class UnzipAPI extends JavascriptAPI {
    public UnzipAPI() {
        super("Unzip");

        registerAsync("unzip", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                Unzipper.unzip((String)args[0], (String)args[1]);
                return null;
            }
        });
    }
}
