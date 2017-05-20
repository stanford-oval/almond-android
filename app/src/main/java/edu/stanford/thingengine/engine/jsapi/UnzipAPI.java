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
