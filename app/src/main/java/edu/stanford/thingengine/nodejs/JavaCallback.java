package edu.stanford.thingengine.nodejs;

/**
 * Created by gcampagn on 5/19/17.
 */

public interface JavaCallback {
    Object invoke(Object... args) throws Exception;
}
