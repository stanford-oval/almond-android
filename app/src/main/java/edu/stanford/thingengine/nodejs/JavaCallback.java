// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.nodejs;

/**
 * Created by gcampagn on 5/19/17.
 */

public interface JavaCallback {
    Object invoke(Object... args) throws Exception;
}
