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

// Some of the code here was copied from nodejs
//
// Copyright Joyent, Inc. and other Node contributors.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.


#include <string>
#include <cstdio>
#include <cstdlib>
#include <thread>
#include <memory>
#include <future>
#include <queue>
#include <list>
#include <unordered_map>

#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <poll.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/utsname.h>
#include <sys/signalfd.h>
#include <dirent.h>
#include <sys/resource.h>

#include <jni.h>
#include <node.h>
#include <uv.h>
#include <libplatform/libplatform.h>
#include <node_buffer.h>

#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include "node-launcher-utils.hpp"
#include "node-launcher-marshal.hpp"
#include "node-launcher-module.hpp"

#include "node-cvc4.hpp"

#define log_error(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define log_warn(tag, ...) __android_log_print(ANDROID_LOG_WARN, tag, __VA_ARGS__)
#define log_info(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)

using v8::V8;
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

namespace node_sqlite3 {
extern node::node_module module;
}

// HACK
namespace node {
namespace tracing {

class TraceEventHelper {
public:
    static v8::TracingController* GetTracingController();
    static void SetTracingController(v8::TracingController* controller);
};

}
}

namespace thingengine_node_launcher {

void CallQueue::run_all_asyncs(uv_async_t *async) {
    CallQueue *self = static_cast<CallQueue*>(async);
    assert (!self->receiver.IsEmpty());

    std::list<PackagedNodeCall> tmp;
    {
        std::unique_lock<std::mutex> lock(self->mutex);
        tmp = std::move(self->calls);
        self->calls.clear();
    }

    v8::Isolate *isolate = self->isolate;

    {
        HandleScope scope(isolate);
        for (PackagedNodeCall &call : tmp) {
            call.Invoke(isolate, self->receiver.Get(isolate), self->this_obj.Get(isolate));
        }

        // run all microtasks before we hit the loop again
        // this will ensure that promises are resolved properly
        isolate->RunMicrotasks();
    }
}

void CallQueue::Init(v8::Isolate *isolate)  {
    std::unique_lock<std::mutex> lock(mutex);
    this->isolate = isolate;
    uv_async_init(uv_default_loop(), this, run_all_asyncs);
}

void CallQueue::SetNodeReceiver(v8::Local<v8::Function> &fn, v8::Local<v8::Object> &this_obj) {
    std::unique_lock<std::mutex> lock(mutex);
    this->receiver = v8::Global<v8::Function>(fn->GetIsolate(), fn);
    this->this_obj = v8::Global<v8::Object>(this_obj->GetIsolate(), this_obj);
    initialized = true;
    init_cond.notify_all();
}

void CallQueue::InvokeAsync(std::u16string &&function_name,
                            std::vector<InteropValue> &&arguments)  {
    std::unique_lock<std::mutex> lock(mutex);
    while (!initialized) init_cond.wait(lock);
    calls.emplace_back(std::move(function_name), std::move(arguments));
    uv_async_send(this);
}

InteropValue CallQueue::InvokeSync(std::u16string &&function_name, std::vector<InteropValue> &&arguments)  {
    std::future<InteropValue> future;

    {
        std::unique_lock<std::mutex> lock(mutex);
        while (!initialized) init_cond.wait(lock);
        calls.emplace_back(std::move(function_name), std::move(arguments));
        future = calls.back().GetReturnValue();
        uv_async_send(this);
    }

    return future.get();
}

void NativeCallQueue::run_all_asyncs(uv_async_t *async) {
    NativeCallQueue *self = static_cast<NativeCallQueue*>(async);

    std::list<std::function<void()>> tmp;
    {
        std::unique_lock<std::mutex> lock(self->mutex);
        tmp = std::move(self->calls);
        self->calls.clear();
    }

    for (auto &fn : tmp) {
        fn();
    }
}

void NativeCallQueue::Init() {
    std::unique_lock<std::mutex> lock(mutex);
    uv_async_init(uv_default_loop(), this, run_all_asyncs);
    initialized = true;
    init_cond.notify_all();
}

void JavaInvoker::Init(JNIEnv *env, jobject jClassLoader) {
    this->env = env;

    jclass ClassLoader = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = env->GetMethodID(ClassLoader, "loadClass",
                                           "(Ljava/lang/String;Z)Ljava/lang/Class;");
    JavaCallback = (jclass) env->NewGlobalRef(env->CallObjectMethod(jClassLoader, loadClass,
                                                                    env->NewStringUTF(
                                                                            "edu/stanford/thingengine/nodejs/JavaCallback"),
                                                                    (jboolean) true));
    NodeJSLauncher = (jclass) env->NewGlobalRef(env->CallObjectMethod(jClassLoader, loadClass,
                                                                      env->NewStringUTF(
                                                                              "edu/stanford/thingengine/nodejs/NodeJSLauncher"),
                                                                      (jboolean) true));
    env->DeleteGlobalRef(jClassLoader);

    this->invoke = env->GetMethodID(JavaCallback, "invoke",
                                    "([Ljava/lang/Object;)Ljava/lang/Object;");

    jclass Executors = env->FindClass("java/util/concurrent/Executors");

    jobject executor = env->CallStaticObjectMethod(Executors, env->GetStaticMethodID(Executors,
                                                                                     "newCachedThreadPool",
                                                                                     "()Ljava/util/concurrent/ExecutorService;"));
    this->executor = env->NewGlobalRef(executor);
    NodeJSLauncher_invokeAsync = env->GetStaticMethodID(NodeJSLauncher, "asyncCallback",
                                                        "(Ljava/util/concurrent/ExecutorService;JLedu/stanford/thingengine/nodejs/JavaCallback;[Ljava/lang/Object;)V");

    if (env->ExceptionOccurred())
        throw std::logic_error("initialization failed");
}

void JavaInvoker::Deinit() {
    std::unique_lock<std::mutex> lock(method_lock);

    env->DeleteGlobalRef(executor);
    env->DeleteGlobalRef(NodeJSLauncher);
    env->DeleteGlobalRef(JavaCallback);
    for (const auto &it : methods) {
        env->DeleteGlobalRef(it.second);
    }
    methods.clear();
    callbacks.clear();
    executor = nullptr;
    env = nullptr;
}

InteropValue JavaInvoker::InvokeSync(v8::Isolate *isolate, std::u16string &&function_name,
                                     std::vector<InteropValue> &&arguments){
    LocalJavaFrame frame(env);

    jobject callback;
    {
        std::unique_lock<std::mutex> lock(method_lock);
        auto it = methods.find(function_name);
        if (it == methods.end()) {
            isolate->ThrowException(v8::Exception::TypeError(
                    string_to_v8(isolate, u"no such function " + function_name)));
            return InteropValue();
        }

        callback = env->NewLocalRef(it->second);
    }

    jobjectArray args = env->NewObjectArray(arguments.size(),
                                            env->FindClass("java/lang/Object"), nullptr);
    for (size_t i = 0; i < arguments.size(); i++) {
        env->SetObjectArrayElement(args, i, arguments[i].ToJava(env));
    }

    jobject result = env->CallObjectMethod(callback, invoke, args);
    if (result == nullptr) {
        jthrowable exception = env->ExceptionOccurred();
        if (exception != nullptr) {
            env->ExceptionClear();
            isolate->ThrowException(java_exception_to_v8(isolate, env, exception));
            return InteropValue();
        }
    }
    InteropValue ret_val;
    if (!ret_val.InitFromJava(env, result, false)) {
        isolate->ThrowException(v8::Exception::TypeError(
                OneByteString(isolate, "failed to marshal return value")));
        return InteropValue();
    }

    return ret_val;
}

v8::Local<v8::Promise> JavaInvoker::InvokeAsync(v8::Isolate *isolate,
                                                std::u16string &&function_name,
                                                std::vector<InteropValue> &&arguments) {
    LocalJavaFrame frame(env);

    Local<v8::Promise::Resolver> resolver = v8::Promise::Resolver::New(isolate);
    jobject callback;
    {
        std::unique_lock<std::mutex> lock(method_lock);
        auto it = methods.find(function_name);
        if (it == methods.end()) {
            resolver->Reject(v8::Exception::TypeError(
                    string_to_v8(isolate, u"no such function " + function_name)));
            return resolver->GetPromise();
        }

        callback = env->NewLocalRef(it->second);
    }

    jobjectArray args = env->NewObjectArray(arguments.size(),
                                            env->FindClass("java/lang/Object"), nullptr);
    for (size_t i = 0; i < arguments.size(); i++) {
        env->SetObjectArrayElement(args, i, arguments[i].ToJava(env));
    }

    jlong promise_id = next_promise_id++;

    v8::Global<v8::Promise::Resolver> &slot = callbacks[promise_id];
    slot = v8::Global<v8::Promise::Resolver>(isolate, resolver);

    env->CallStaticVoidMethod(NodeJSLauncher, NodeJSLauncher_invokeAsync, executor, promise_id,
                              callback, args);
    jthrowable exception = env->ExceptionOccurred();
    if (exception != nullptr) {
        env->ExceptionClear();
        Local<Value> v8_exception = java_exception_to_v8(isolate, env, exception);
        resolver->Reject(v8_exception);
        callbacks.erase(promise_id);
    }

    return resolver->GetPromise();
}

void JavaInvoker::CompletePromise(jlong promiseId, const v8::Maybe<std::u16string> &error_msg,
                                  const InteropValue &result) {
    Isolate *isolate = Isolate::GetCurrent();
    HandleScope handle_scope(isolate);

    auto fn_it = callbacks.find(promiseId);
    if (fn_it == callbacks.end())
        return;

    Local<v8::Promise::Resolver> fn = fn_it->second.Get(isolate);
    callbacks.erase(fn_it);

    if (error_msg.IsNothing())
        fn->Resolve(result.ToJavaScript(isolate));
    else
        fn->Reject(exception_to_v8(isolate, error_msg.FromJust()));
}

static bool ShouldAbortOnUncaughtException(Isolate *isolate) {
    // FIXME for now we never abort for uncaught exceptions
    return false;
}

static int
loop(node::Environment *env, Isolate *isolate, uv_loop_t *event_loop, v8::Platform *platform) {
    SealHandleScope seal(isolate);
    bool more;
    do {
        v8::platform::PumpMessageLoop(platform, isolate);
        more = (bool) uv_run(event_loop, UV_RUN_ONCE);

        if (!more) {
            v8::platform::PumpMessageLoop(platform, isolate);
            EmitBeforeExit(env);

            // Emit `beforeExit` if the loop became alive either after emitting
            // event, or after running some callbacks.
            more = (bool) uv_loop_alive(event_loop);
            if (uv_run(event_loop, UV_RUN_NOWAIT) != 0)
                more = true;
        }
    } while (more && !global_state.terminate);

    global_state.launcher_module.Reset();

    int exit_code;
    if (!global_state.terminate) {
        exit_code = EmitExit(env);
    } else {
        Isolate::Scope isolate_scope(isolate);
        HandleScope handle_scope(isolate);
        exit_code = node::GetProcessObject(env)->Get(
                OneByteString(isolate, "exitCode"))->Int32Value();
    }
    RunAtExit(env);
    return exit_code;
}

class ArrayBufferAllocator : public v8::ArrayBuffer::Allocator {
public:
    ArrayBufferAllocator() {}

    inline uint32_t* zero_fill_field() { return &zero_fill_field_; }

    virtual void *Allocate(size_t size) {
        return calloc(size, 1);
    }

    virtual void *AllocateUninitialized(size_t size) {
        return malloc(size);
    }

    virtual void Free(void *data, size_t) {
        free(data);
    }

private:
    uint32_t zero_fill_field_ = 1;  // Boolean but exposed as uint32 to JS land.
};

static std::unique_ptr<char[]>
read_full_asset(AAsset *asset) {
    off64_t length = AAsset_getLength64(asset);

    std::unique_ptr<char[]> buffer(new char[length]);
    off64_t off = 0;

    while (off < length) {
        int read = AAsset_read(asset, &buffer[off], (size_t) (length - off));
        off += read;
    }

    AAsset_close(asset);
    return buffer;
}

template<typename T, void (*fn)(T*)>
class free_func {
public:
    void operator()(T* obj) {
        fn(obj);
    }
};

static void
start_node(JavaVM *vm, AAsset *app_code, jobject jClassLoader) {
    static const char *argv[] = {"node", nullptr};
    int argc = 1;
    int exec_argc;
    const char **exec_argv;

    JNIEnv *jnienv;
    vm->AttachCurrentThread(&jnienv, nullptr);
    global_state.java_invoker.Init(jnienv, jClassLoader);
    global_state.native_queue.Init();

    node_module_register(&launcher_module);
    node_module_register(&node_sqlite3::module);
    node_module_register(&node_cvc4::module);
    node::Init(&argc, const_cast<const char **>(argv), &exec_argc, &exec_argv);

    std::unique_ptr<v8::Platform> platform(v8::platform::CreateDefaultPlatform());
    V8::InitializePlatform(platform.get());
    node::tracing::TraceEventHelper::SetTracingController(new v8::TracingController());
    V8::Initialize();

    std::unique_ptr<ArrayBufferAllocator> array_buffer_allocator(new ArrayBufferAllocator());
    Isolate::CreateParams params;
    params.array_buffer_allocator = array_buffer_allocator.get();
    params.code_event_handler = nullptr;
    Isolate *isolate = Isolate::New(params);
    global_state.queue.Init(isolate);

    {
        Locker locker(isolate);
        Isolate::Scope isolate_scope(isolate);
        HandleScope handle_scope(isolate);
        std::unique_ptr<node::IsolateData, free_func<node::IsolateData, node::FreeIsolateData>>
                isolate_data(node::CreateIsolateData(isolate, uv_default_loop()));

        Local<Context> context = Context::New(isolate);

        // where the magic happens!
        Context::Scope context_scope(context);
        node::Environment *env = node::CreateEnvironment(isolate_data.get(), context,
                                                         argc, argv, exec_argc, exec_argv);
        Local<Object> process = node::GetProcessObject(env);
        std::unique_ptr<char[]> code_buffer = read_full_asset(app_code);
        process->DefineOwnProperty(context, OneByteString(isolate, "_eval"),
                                   String::NewFromUtf8(isolate, code_buffer.get()),
                                   v8::PropertyAttribute::None).FromJust();
        code_buffer = nullptr;
        node::LoadEnvironment(env);
        //assert(!global_state.launcher_module.IsEmpty());

        isolate->SetAbortOnUncaughtExceptionCallback(ShouldAbortOnUncaughtException);
        log_info("nodejs", "NodeJS initialized");

        int exit_code = loop(env, isolate, uv_default_loop(), platform.get());
        log_info("nodejs", "NodeJS code exited with code %d", exit_code);

        FreeEnvironment(env);
    }

    isolate->Dispose();
    V8::Dispose();
    V8::ShutdownPlatform();
    delete[] exec_argv;

    global_state.java_invoker.Deinit();
    vm->DetachCurrentThread();
}

static bool
make_args(std::vector<InteropValue> &vector, JNIEnv *env, jobjectArray args) {
    vector.resize((size_t) env->GetArrayLength(args));
    for (size_t i = 0; i < vector.size(); i++) {
        if (!vector[i].InitFromJava(env, env->GetObjectArrayElement(args, i), true))
            return false;
    }
    return true;
}

}

using namespace thingengine_node_launcher;

extern "C" {

JNIEXPORT void JNICALL
Java_edu_stanford_thingengine_nodejs_NodeJSLauncher_registerJavaCall(JNIEnv *env, jclass type,
                                                                     jstring fn_,
                                                                     jobject callback) {
    global_state.java_invoker.Register(jstring_to_string(env, fn_), env->NewGlobalRef(callback));
}

JNIEXPORT void JNICALL
Java_edu_stanford_thingengine_nodejs_NodeJSLauncher_completePromise(JNIEnv *env, jclass type,
                                                                    jlong promiseId,
                                                                    jobject result,
                                                                    jthrowable error) {
    // until we have c++17 std::optional
    v8::Maybe<std::u16string> error_msg = v8::Nothing<std::u16string>();

    if (error != nullptr) {
        jclass Throwable = env->FindClass("java/lang/Throwable");
        jmethodID getMessage = env->GetMethodID(Throwable, "getMessage", "()Ljava/lang/String;");
        jstring message = (jstring) env->CallObjectMethod(error, getMessage);
        error_msg = v8::Just<std::u16string>(jstring_to_string(env, message));
    }

    InteropValue value;
    if (!value.InitFromJava(env, result, true))
        return;

    global_state.native_queue.InvokeAsync(
            std::bind(&JavaInvoker::CompletePromise, std::ref(global_state.java_invoker), promiseId,
                      std::move(error_msg), std::move(value)));
}

JNIEXPORT void JNICALL
Java_edu_stanford_thingengine_nodejs_NodeJSLauncher_invokeAsync(JNIEnv *env, jclass type,
                                                                jstring fn_, jobjectArray args) {
    std::vector<InteropValue> interop_args;

    if (!make_args(interop_args, env, args))
        return;
    global_state.queue.InvokeAsync(jstring_to_string(env, fn_), std::move(interop_args));
}

JNIEXPORT jobject JNICALL
Java_edu_stanford_thingengine_nodejs_NodeJSLauncher_invokeSync(JNIEnv *env, jclass type,
                                                               jstring fn_, jobjectArray args) {
    std::vector<InteropValue> interop_args;

    if (!make_args(interop_args, env, args))
        return nullptr;

    try {
        InteropValue return_value = global_state.queue.InvokeSync(jstring_to_string(env, fn_),
                                                                  std::move(interop_args));
        return return_value.ToJava(env);
    } catch (const js_exception &js) {
        jclass RuntimeException = env->FindClass("java/lang/RuntimeException");
        jobject e = env->NewObject(RuntimeException, env->GetMethodID(RuntimeException, "<init>",
                                                                      "(Ljava/lang/String;)V"),
                                   string_to_jstring(env, js.message));
        env->Throw((jthrowable) e);
        return nullptr;
    } catch (const std::exception &e) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL
Java_edu_stanford_thingengine_nodejs_NodeJSLauncher_launchNodeNative(JNIEnv *env, jclass type,
                                                                     jobject jAssetManager,
                                                                     jobject jClassLoader) {
    AAssetManager *assetManager = AAssetManager_fromJava(env, jAssetManager);
    AAsset *asset = AAssetManager_open(assetManager, "app.js", AASSET_MODE_STREAMING);

    JavaVM *vm;
    env->GetJavaVM(&vm);
    std::thread(start_node, vm, asset, env->NewGlobalRef(jClassLoader)).detach();
    return nullptr;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}

}
