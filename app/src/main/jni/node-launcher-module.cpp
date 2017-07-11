//
// Created by gcampagn on 7/10/17.
//

#include <vector>
#include <android/log.h>

#include "node-launcher-module.hpp"
#include "node-launcher-marshal.hpp"

#define log_error(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define log_warn(tag, ...) __android_log_print(ANDROID_LOG_WARN, tag, __VA_ARGS__)
#define log_info(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)

using v8::Isolate;
using v8::Locker;
using v8::HandleScope;
using v8::Local;
using v8::Context;
using v8::Object;
using v8::String;
using v8::Value;
using v8::SealHandleScope;
using v8::FunctionCallbackInfo;

namespace thingengine_node_launcher {

static void console_log(const FunctionCallbackInfo<Value> &args) {
    String::Utf8Value msg(args[0]->ToString());
    log_info("nodejs", "%s", *msg);
    args.GetReturnValue().SetUndefined();
}

static void console_error(const FunctionCallbackInfo<Value> &args) {
    String::Utf8Value msg(args[0]->ToString());
    log_error("nodejs", "%s", *msg);
    args.GetReturnValue().SetUndefined();
}

static void console_warn(const FunctionCallbackInfo<Value> &args) {
    String::Utf8Value msg(args[0]->ToString());
    log_warn("nodejs", "%s", *msg);
    args.GetReturnValue().SetUndefined();
}

static void process_exit(const FunctionCallbackInfo<Value> &args) {
    global_state.terminate = true;
}

static void set_async_receiver(const FunctionCallbackInfo<Value> &args) {
    Local<v8::Function> receiver = args[0].As<v8::Function>();
    Local<v8::Object> this_obj = args[0].As<v8::Object>();
    global_state.queue.SetNodeReceiver(receiver, this_obj);
}

static void call_java_async(const FunctionCallbackInfo<Value> &args) {
    Local<String> fn = args[0].As<String>();
    size_t first_arg = 1, end_arg = (size_t) args.Length();

    std::vector <InteropValue> arguments(end_arg - first_arg);
    for (size_t i = first_arg; i < end_arg; i++) {
        if (!arguments[i - first_arg].InitFromJS(args.GetIsolate(), args[i]))
            return;
    }

    args.GetReturnValue().Set(
            global_state.java_invoker.InvokeAsync(args.GetIsolate(), v8_to_string(fn),
                                                  std::move(arguments)));
}

static void call_java_sync(const FunctionCallbackInfo<Value> &args) {
    Local<String> fn = args[0].As<String>();
    size_t first_arg = 1, end_arg = (size_t) args.Length();

    std::vector <InteropValue> arguments(end_arg - first_arg);
    for (size_t i = first_arg; i < end_arg; i++) {
        if (!arguments[i - first_arg].InitFromJS(args.GetIsolate(), args[i]))
            return;
    }

    InteropValue ret_value = global_state.java_invoker.InvokeSync(args.GetIsolate(),
                                                                  v8_to_string(fn),
                                                                  std::move(arguments));
    if (ret_value.IsNull())
        args.GetReturnValue().SetNull();
    else
        args.GetReturnValue().Set(ret_value.ToJavaScript(args.GetIsolate()));
}

static void register_module(Local<Object> exports, Local<Value>, void *) {
    global_state.launcher_module = v8::Global<Object>(exports->GetIsolate(), exports);

    NODE_SET_METHOD(exports, "log", console_log);
    NODE_SET_METHOD(exports, "error", console_error);
    NODE_SET_METHOD(exports, "warn", console_warn);
    NODE_SET_METHOD(exports, "exit", process_exit);
    NODE_SET_METHOD(exports, "setAsyncReceiver", set_async_receiver);
    NODE_SET_METHOD(exports, "callJavaAsync", call_java_async);
    NODE_SET_METHOD(exports, "callJavaSync", call_java_sync);
}

node::node_module launcher_module = {
        .nm_version = NODE_MODULE_VERSION,
        .nm_flags = 0,
        .nm_dso_handle = nullptr,
        .nm_filename = __FILE__,
        .nm_register_func = register_module,
        .nm_context_register_func = nullptr,
        .nm_modname = "android-launcher",
        .nm_priv = nullptr,
        .nm_link = nullptr
};
GlobalState global_state;

}