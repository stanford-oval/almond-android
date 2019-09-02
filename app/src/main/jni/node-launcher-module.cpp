// -*- mode: c++; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// Copyright 2017 The Board of Trustees of the Leland Stanford Junior University
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by the
// Free Software Foundation; either version 2 of the License, or (at your
// option) any later version.
//
// This program is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
// or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
//
// Author: Giovanni Campagna <gcampagn@cs.stanford.edu>

#include <vector>
#include <android/log.h>
#include <android/asset_manager.h>
#include <node_buffer.h>

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

static void init(const FunctionCallbackInfo<Value>& args) {
    global_state.queue.Init(args.GetIsolate());
}

static void read_asset_sync(const FunctionCallbackInfo<Value>& args) {
    auto assetname = v8_to_utf8(args[0].As<String>());

    AAsset *asset = AAssetManager_open(global_state.asset_manager, assetname.c_str(), AASSET_MODE_STREAMING);
    off64_t length = AAsset_getLength64(asset);

    char* buffer = new char[length];
    off64_t off = 0;

    while (off < length) {
        int read = AAsset_read(asset, &buffer[off], (size_t) (length - off));
        off += read;
    }

    AAsset_close(asset);

    auto nodebuffer = node::Buffer::New(args.GetIsolate(), buffer, length).ToLocalChecked();
    args.GetReturnValue().Set(nodebuffer);
}


static void register_module(Local<Object> exports, Local<Value>, void *) {
    NODE_SET_METHOD(exports, "init", init);
    NODE_SET_METHOD(exports, "readAssetSync", read_asset_sync);
    NODE_SET_METHOD(exports, "log", console_log);
    NODE_SET_METHOD(exports, "error", console_error);
    NODE_SET_METHOD(exports, "warn", console_warn);
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
