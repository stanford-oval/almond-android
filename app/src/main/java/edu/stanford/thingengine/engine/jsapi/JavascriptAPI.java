package edu.stanford.thingengine.engine.jsapi;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import edu.stanford.thingengine.nodejs.JavaCallback;
import edu.stanford.thingengine.nodejs.NodeJSLauncher;

/**
 * Created by gcampagn on 10/26/15.
 */
public abstract class JavascriptAPI {
    private final String name;

    @Deprecated
    public interface GenericCall {
        Object run(Object... args) throws Exception;
    }

    public JavascriptAPI(String name) {
        this.name = name;
    }

    protected void invokeAsync(String callback, Object value) {
        NodeJSLauncher.invokeAsync(name + "_" + callback, null, value);
    }

    protected void  register(String callback, JavaCallback call) {
        NodeJSLauncher.registerJavaCall(name + "_" + callback, call);
    }

    protected void registerAsync(String callback, final GenericCall call) {
        register(callback, new JavaCallback() {
            @Override
            public Object invoke(Object... args) throws Exception {
                return call.run(args);
            }
        });
    }

    protected void registerAsync(String callback, final Callable<?> callable) {
        register(callback, new JavaCallback() {
            @Override
            public Object invoke(Object... args) throws Exception {
                return callable.call();
            }
        });
    }

    protected void registerAsync(String callback, final Runnable runnable) {
        registerAsync(callback, Executors.callable(runnable));
    }

    protected void registerSync(String callback, final GenericCall call) {
        registerAsync(callback, call);
    }

    protected void registerSync(String callback, Runnable runnable) {
        registerAsync(callback, runnable);
    }
}
