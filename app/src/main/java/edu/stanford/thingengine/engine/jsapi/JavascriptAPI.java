package edu.stanford.thingengine.engine.jsapi;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import edu.stanford.thingengine.engine.service.ControlChannel;
import io.jxcore.node.jxcore;

/**
 * Created by gcampagn on 10/26/15.
 */
public abstract class JavascriptAPI {
    private final String name;
    private final ControlChannel control;

    public interface GenericCall {
        Object run(Object... args) throws Exception;
    }

    public JavascriptAPI(String name, ControlChannel control) {
        this.name = name;
        this.control = control;
    }

    protected ControlChannel getControl() {
        return control;
    }

    private void sendCallback(String callback, String error, Object value) {
        control.sendInvokeCallback(callback, error, value);
    }

    protected void invokeAsync(String callback, Object value) {
        sendCallback(name + "_" + callback, null, value);
    }

    protected void registerAsync(String callback, final GenericCall call) {
        jxcore.RegisterMethod(name + "_" + callback, new jxcore.JXcoreCallback() {
            @Override
            public void Receiver(final ArrayList<Object> params, final String callbackId) {
                control.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        Object result = null;
                        Exception error = null;
                        try {
                            result = call.run(params.toArray());
                        } catch (Exception e) {
                            error = e;
                        }
                        if (error != null)
                            sendCallback(callbackId, error.getMessage(), null);
                        else
                            sendCallback(callbackId, null, result);
                    }
                });
            }
        });
    }

    protected void registerAsync(String callback, final Callable<?> callable) {
        registerAsync(callback, new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return callable.call();
            }
        });
    }

    protected void registerAsync(String callback, final Runnable runnable) {
        registerAsync(callback, Executors.callable(runnable));
    }

    protected void registerSync(String callback, final GenericCall call) {
        jxcore.RegisterMethod(name + "_" + callback, new jxcore.JXcoreCallback() {
            @Override
            public void Receiver(ArrayList<Object> params, String callbackId) {
                try {
                    Object result = call.run(params.toArray());
                    jxcore.CallJSMethod(callbackId, new Object[]{null, result});
                } catch (Exception e) {
                    jxcore.CallJSMethod(callbackId, new Object[]{e.getMessage(), null});
                }
            }
        });
    }

    protected void registerSync(String callback, final Callable<?> callable) {
        registerSync(callback, new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return callable.call();
            }
        });
    }

    protected void registerSync(String callback, Runnable runnable) {
        registerSync(callback, Executors.callable(runnable));
    }
}
