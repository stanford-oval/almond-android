// -*- mode: c++; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// Copyright 2017 Giovanni Campagna <gcampagn@cs.stanford.edu>
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
#include "node/node.h"
#include "node/uv.h"
#include "node/libplatform/libplatform.h"
#include "node/node_buffer.h"

#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

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

static std::u16string
jstring_to_string(JNIEnv *env, jstring str)
{
    jboolean is_copy;
    const jchar *jstr = env->GetStringChars(str, &is_copy);
    std::u16string s((char16_t*)jstr, (size_t)env->GetStringLength(str));
    env->ReleaseStringChars(str, jstr);
    return s;
}

static std::u16string
jobject_to_string(JNIEnv *env, jobject obj)
{
    jclass Object = env->FindClass("java/lang/Object");
    jmethodID toString = env->GetMethodID(Object, "toString", "()Ljava/lang/String;");
    jstring str = (jstring) env->CallObjectMethod(obj, toString);
    return jstring_to_string(env, str);
}

static jstring
string_to_jstring(JNIEnv *env, const std::u16string& str)
{
    return env->NewString((const jchar*)str.data(), str.length());
}

static Local<String>
string_to_v8(Isolate *isolate, const std::u16string& s)
{
    return v8::String::NewFromTwoByte(isolate, (const uint16_t*)s.data(), v8::String::NewStringType::kNormalString, s.length());
}

static std::basic_string<char16_t>
v8_to_string(const Local<v8::String>& s)
{
    int length = s->Length();
    char16_t* buffer = new char16_t[length];

    s->Write((uint16_t*)buffer, 0, -1, v8::String::NO_NULL_TERMINATION);
    std::basic_string<char16_t> stdstring = std::basic_string<char16_t>(buffer, (size_t)length);
    delete[] buffer;
    return stdstring;
}

static v8::Local<v8::Value>
java_exception_to_v8(Isolate *isolate, JNIEnv *env, jthrowable throwable)
{
    jclass Throwable = env->FindClass("java/lang/Throwable");
    jmethodID getMessage = env->GetMethodID(Throwable, "getMessage", "()Ljava/lang/String;");
    jstring str = (jstring)env->CallObjectMethod(throwable, getMessage);

    jsize str_len = env->GetStringLength(str);
    const jchar *str_chars = env->GetStringChars(str, nullptr);
    Local<String> v8_message = v8::String::NewFromTwoByte(isolate, str_chars, v8::String::NewStringType::kNormalString, str_len);
    env->ReleaseStringChars(str, str_chars);

    return v8::Exception::Error(v8_message);
}

static v8::Local<v8::Value>
exception_to_v8(Isolate *isolate, const std::u16string& error_msg)
{
    Local<String> v8_message = v8::String::NewFromTwoByte(isolate, (uint16_t*)error_msg.data(), v8::String::NewStringType::kNormalString, error_msg.size());
    return v8::Exception::Error(v8_message);
}

inline v8::Local<v8::String> OneByteString(v8::Isolate* isolate,
                                           const char* data,
                                           int length) {
    return v8::String::NewFromOneByte(isolate,
                                      reinterpret_cast<const uint8_t*>(data),
                                      v8::NewStringType::kNormal,
                                      length).ToLocalChecked();
}

inline v8::Local<v8::String> OneByteString(v8::Isolate* isolate,
                                           const char* data) {
    return OneByteString(isolate, data, strlen(data));
}

class ByteBuffer
{
private:
    uint8_t *data;
    size_t len;

public:
    ByteBuffer() : data(nullptr), len(0) {}
    ~ByteBuffer()
    {
        free(data);
    }

    ByteBuffer(const ByteBuffer& other)
    {
        data = (uint8_t*)malloc(other.len);
        len = other.len;
        if (data == nullptr)
            throw std::bad_alloc();
        memcpy(data, other.data, len);
    }

    ByteBuffer(ByteBuffer&& other)
    {
        data = other.data;
        other.data = nullptr;
        len = other.len;
        other.len = 0;
    }

    ByteBuffer(Isolate *isolate, const Local<v8::Object>& node_buffer)
    {
        len = node::Buffer::Length(node_buffer);
        data = (uint8_t*)malloc(len);
        memcpy(data, node::Buffer::Data(node_buffer), len);
    }

    ByteBuffer(JNIEnv *env, jbyteArray byteArray)
    {
        jbyte *bytes;
        bytes = env->GetByteArrayElements(byteArray, nullptr);
        len = (size_t)env->GetArrayLength(byteArray);
        data = (uint8_t*)malloc(len);
        memcpy(data, bytes, len);
        env->ReleaseByteArrayElements(byteArray, bytes, JNI_ABORT);
    }

    Local<v8::Object> ToJavaScript(Isolate *isolate) const
    {
        return node::Buffer::Copy(isolate, (char*)data, len).ToLocalChecked();
    }

    jbyteArray ToJava(JNIEnv *env) const
    {
        jbyteArray array = env->NewByteArray(len);
        jbyte *bytes;
        bytes = env->GetByteArrayElements(array, nullptr);
        memcpy(bytes, data, len);
        env->ReleaseByteArrayElements(array, bytes, 0);
        return array;
    }
};

class InteropValue {
    enum class Type { empty, boolean, string, number, json, buffer, null };

    typedef std::u16string String;

private:
    Type type;
    union {
        bool b;
        std::aligned_storage<sizeof(String), std::alignment_of<String>::value> s;
        double n;
        std::aligned_storage<sizeof(ByteBuffer), std::alignment_of<ByteBuffer>::value> buf_space;
    } storage;

    String& string() {
        assert (type == Type::string);
        return reinterpret_cast<String&>(storage.s);
    }
    const String& string() const {
        assert (type == Type::string);
        return reinterpret_cast<const String&>(storage.s);
    }

    ByteBuffer& buf() {
        assert (type == Type::buffer);
        return reinterpret_cast<ByteBuffer&>(storage.buf_space);
    }
    const ByteBuffer& buf() const {
        assert (type == Type::buffer);
        return reinterpret_cast<const ByteBuffer&>(storage.buf_space);
    }

    double& n() {
        return storage.n;
    }
    const double& n() const {
        return storage.n;
    }

    bool& b() {
        return storage.b;
    }
    const bool& b() const {
        return storage.b;
    }

public:
    InteropValue() : type(Type::empty) {}

    InteropValue(const InteropValue& v) : type(v.type)
    {
        if (type == Type::string || type == Type::json)
            new (&storage.s) String(v.string());
        else if (type == Type::buffer)
            new (&storage.buf_space) ByteBuffer(v.buf());
        else
            storage = v.storage;
    }

    InteropValue(InteropValue&& v) : type(v.type)
    {
        if (type == Type::string || type == Type::json)
            new (&storage.s) String(std::move(v.string()));
        else if (type == Type::buffer)
            new (&storage.buf_space) ByteBuffer(std::move(v.buf()));
        else
            storage = v.storage;
    }

    ~InteropValue()
    {
        if (type == Type::string || type == Type::json)
            string().~String();
        else if (type == Type::buffer)
            buf().~ByteBuffer();
    }

    bool IsEmpty() const
    {
        return type == Type::empty;
    }

    bool IsNull() const
    {
        return type == Type::null;
    }

    bool InitFromJS(Isolate *isolate, Local<Value> value)
    {
        assert(type == Type::empty);

        if (value->IsNumber()) {
            type = Type::number;
            n() = value->NumberValue(isolate->GetCurrentContext()).FromJust();
        } else if (value->IsBoolean()) {
            type = Type::boolean;
            b() = value->BooleanValue(isolate->GetCurrentContext()).FromJust();
        } else if (value->IsString()) {
            type = Type::string;
            new (&storage.s) std::u16string(v8_to_string(value->ToString(isolate->GetCurrentContext()).ToLocalChecked()));
        } else if (value->IsUndefined() || value->IsNull()) {
            type = Type::null;
        } else if (node::Buffer::HasInstance(value)) {
            type = Type::buffer;
            new (&storage.buf_space) ByteBuffer(isolate, value.As<Object>());
        } else if (value->IsObject()) {
            // object
            Local<Value> JSON = isolate->GetCurrentContext()->Global()->Get(OneByteString(isolate, "JSON"));
            Local<Value> stringify = JSON.As<Object>()->Get(OneByteString(isolate, "stringify"));
            Local<Value> result = stringify.As<v8::Function>()->Call(isolate->GetCurrentContext()->Global(), 1, &value);
            type = Type::json;
            new (&storage.s) std::u16string(v8_to_string(result->ToString(isolate->GetCurrentContext()).ToLocalChecked()));
        } else {
            return false;
        }

        return true;
    }

    bool InitFromJava(JNIEnv *env, jobject obj, bool do_throw = true) {
        assert(type == Type::empty);

        jclass Number = env->FindClass("java/lang/Number");
        jclass String = env->FindClass("java/lang/String");
        jclass Boolean = env->FindClass("java/lang/Boolean");
        jclass JSONObject = env->FindClass("org/json/JSONObject");
        jclass JSONArray = env->FindClass("org/json/JSONArray");
        jclass byteArray = env->FindClass("[B");
        if (obj == nullptr) {
            type = Type::null;
        } else if (env->IsInstanceOf(obj, Number)) {
            type = Type::number;
            n() = env->CallDoubleMethod(obj, env->GetMethodID(Number, "doubleValue", "()D"));
        } else if (env->IsInstanceOf(obj, String)) {
            type = Type::string;
            new (&storage.s) std::u16string(jstring_to_string(env, (jstring)obj));
        } else if (env->IsInstanceOf(obj, Boolean)) {
            type = Type::boolean;
            b() = env->CallBooleanMethod(obj, env->GetMethodID(Boolean, "booleanValue", "()Z"));
        } else if (env->IsInstanceOf(obj, byteArray)) {
            type = Type::buffer;
            new (&storage.buf_space) ByteBuffer(env, (jbyteArray)obj);
        } else if (env->IsInstanceOf(obj, JSONObject) || env->IsInstanceOf(obj, JSONArray)) {
            type = Type::json;
            new (&storage.s) std::u16string(jobject_to_string(env, obj));
        } else {
            if (do_throw)
                env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid argument type");
            return false;
        }

        return true;
    }

    Local<Value> ToJavaScript(Isolate *isolate) const {
        switch(type) {
            case Type::empty:
                return Local<Value>();
            case Type::number:
                return v8::Number::New(isolate, n());
            case Type::boolean:
                return v8::Boolean::New(isolate, b());
            case Type::string:
                return string_to_v8(isolate, string());
            case Type::buffer:
                return buf().ToJavaScript(isolate);
            case Type::json:
                return v8::JSON::Parse(isolate, string_to_v8(isolate, string())).ToLocalChecked();
            case Type::null:
                return v8::Null(isolate);
        }
    }

    jobject ToJava(JNIEnv *env) const {
        switch(type) {
            case Type::empty:
            case Type::null:
                return nullptr;
            case Type::number: {
                jclass Double = env->FindClass("java/lang/Double");
                return env->NewObject(Double, env->GetMethodID(Double, "<init>", "(D)V"),
                                      (jdouble) n());
            }
            case Type::boolean: {
                jclass Boolean = env->FindClass("java/lang/Boolean");
                return env->NewObject(Boolean, env->GetMethodID(Boolean, "<init>", "(Z)V"),
                                      (jboolean) b());
            }
            case Type::string:
                return string_to_jstring(env, string());
            case Type::buffer:
                return buf().ToJava(env);
            case Type::json: {
                jclass JSONTokener = env->FindClass("org/json/JSONTokener");
                jobject jsonTokener = env->NewObject(JSONTokener, env->GetMethodID(JSONTokener, "<init>", "(Ljava/lang/String;)V"), string_to_jstring(env, string()));
                return env->CallObjectMethod(jsonTokener, env->GetMethodID(JSONTokener, "next", "()Ljava/lang/Object;"));
            }
        }
    }
};

struct js_exception {
    std::basic_string<char16_t> message;

    js_exception(const std::basic_string<char16_t>& msg) : message(msg) {}
};

struct PackagedNodeCall {
private:
    std::vector<InteropValue> m_args;
    std::basic_string<char16_t> m_fn;
    std::promise<InteropValue> m_return;
    bool use_return;

    Local<v8::String> GetFunctionName(Isolate *isolate) const
    {
        return string_to_v8(isolate, m_fn);
    }

    Local<v8::Array> ArgumentsToJS(Isolate *isolate) const
    {
        Local<v8::Array> arguments = v8::Array::New(isolate);
        uint32_t i = 0;
        for (const InteropValue& v : m_args) {
            arguments->Set(i++, v.ToJavaScript(isolate));
        }
        return arguments;
    }

    void SetReturn(Isolate *isolate, Local<Value> value)
    {
        if (!use_return)
            return;
        InteropValue ret;
        if (ret.InitFromJS(isolate, value))
            m_return.set_value(std::move(ret));
        else
            m_return.set_exception(std::make_exception_ptr(std::invalid_argument("argument cannot be marshalled")));
    }

    void SetException(Isolate *isolate, Local<Value> value)
    {
        Local<String> message = value->ToDetailString(isolate->GetCurrentContext()).ToLocalChecked();
        if (!use_return) {
            String::Utf8Value msg(message);
            log_error("nodejs", "Lost exception %s", *msg);
            return;
        }

        m_return.set_exception(std::make_exception_ptr(js_exception(v8_to_string(message))));
    }

    static void then_promise(const v8::FunctionCallbackInfo<Value>& info)
    {
        Local<Value> value;
        if (info.Length() == 0)
            value = v8::Undefined(info.GetIsolate());
        else
            value = info[0];

        std::promise<InteropValue>* promise = static_cast<std::promise<InteropValue>*>(info.Data().As<Object>()->GetAlignedPointerFromInternalField(0));
        if (promise == nullptr)
            return;
        info.Data().As<Object>()->SetAlignedPointerInInternalField(0, nullptr);

        InteropValue ret;
        if (ret.InitFromJS(info.GetIsolate(), value))
            promise->set_value(std::move(ret));
        else
            promise->set_exception(std::make_exception_ptr(std::invalid_argument("argument cannot be marshalled")));

        delete promise;
    }
    static void catch_promise(const v8::FunctionCallbackInfo<Value>& info)
    {
        if (info.Length() == 0)
            return;

        Local<Value> value = info[0];

        std::promise<InteropValue>* promise = static_cast<std::promise<InteropValue>*>(info.Data().As<Object>()->GetAlignedPointerFromInternalField(0));
        if (promise == nullptr)
            return;
        info.Data().As<Object>()->SetAlignedPointerInInternalField(0, nullptr);

        Local<String> message = value->ToDetailString(info.GetIsolate()->GetCurrentContext()).ToLocalChecked();
        promise->set_exception(std::make_exception_ptr(js_exception(v8_to_string(message))));
        delete promise;
    }

public:

    PackagedNodeCall(std::basic_string<char16_t>&& _fn, std::vector<InteropValue>&& _arguments) :
            m_fn(std::move(_fn)),
            m_args(std::move(_arguments)),
            use_return(false)
    {}

    std::future<InteropValue> GetReturnValue()
    {
        use_return = true;
        return m_return.get_future();
    }

    void Invoke(Isolate *isolate, const Local<v8::Function>& receiver, const Local<v8::Object>& this_obj)
    {
        Local<v8::String> fn = GetFunctionName(isolate);
        Local<v8::Array> args = ArgumentsToJS(isolate);

        Local<v8::Value> async_argv[2] = { fn, args };

        v8::TryCatch try_catch(isolate);
        Local<Value> ret_value = node::MakeCallback(isolate, this_obj, receiver, 2, async_argv);
        if (try_catch.HasCaught()) {
            SetException(isolate, try_catch.Exception());
        } else if (ret_value->IsPromise()) {
            if (!use_return)
                return;
            std::promise<InteropValue> *return_copy = new std::promise<InteropValue>(
                    std::move(m_return));

            Local<v8::ObjectTemplate> tmpl = v8::ObjectTemplate::New(isolate);
            tmpl->SetInternalFieldCount(1);
            Local<Object> return_copy_gced = tmpl->NewInstance();
            return_copy_gced->SetAlignedPointerInInternalField(0, return_copy);

            Local<v8::Function> then = v8::Function::New(isolate, &then_promise, return_copy_gced, 1);
            Local<v8::Function> _catch = v8::Function::New(isolate, &catch_promise, return_copy_gced, 1);
            ret_value.As<v8::Promise>()->Then(then)->Catch(_catch);
        } else {
            SetReturn(isolate, ret_value);
        }
    }
};

class CallQueue {
private:
    uv_async_t async;
    std::list<PackagedNodeCall> calls;
    std::mutex mutex;
    std::condition_variable init_cond;
    Isolate *isolate;
    v8::Global<v8::Function> receiver;
    v8::Global<v8::Object> this_obj;
    bool initialized;

    static void run_all_asyncs(uv_async_t *_async)
    {
        CallQueue *self = (CallQueue*)(((char*)_async)-offsetof(CallQueue, async));
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
            for (PackagedNodeCall& call : tmp) {
                call.Invoke(isolate, self->receiver.Get(isolate), self->this_obj.Get(isolate));
            }
        }
    }

public:
    void Init(Isolate *isolate)
    {
        std::unique_lock<std::mutex> lock(mutex);
        this->isolate = isolate;
        uv_async_init(uv_default_loop(), &async, run_all_asyncs);
    }

    void SetNodeReceiver(Local<v8::Function>& fn, Local<v8::Object>& this_obj)
    {
        std::unique_lock<std::mutex> lock(mutex);
        this->receiver = v8::Global<v8::Function>(fn->GetIsolate(), fn);
        this->this_obj = v8::Global<v8::Object>(this_obj->GetIsolate(), this_obj);
        initialized = true;
        init_cond.notify_all();
    }

    void InvokeAsync(std::u16string&& function_name, std::vector<InteropValue>&& arguments)
    {
        std::unique_lock<std::mutex> lock(mutex);
        while (!initialized) init_cond.wait(lock);
        calls.emplace_back(std::move(function_name), std::move(arguments));
        uv_async_send(&async);
    }

    InteropValue InvokeSync(std::u16string&& function_name, std::vector<InteropValue>&& arguments)
    {
        std::future<InteropValue> future;

        {
            std::unique_lock<std::mutex> lock(mutex);
            while (!initialized) init_cond.wait(lock);
            calls.emplace_back(std::move(function_name), std::move(arguments));
            future = calls.back().GetReturnValue();
            uv_async_send(&async);
        }

        return future.get();
    }
};

class NativeCallQueue
{
private:
    uv_async_t async;
    std::list<std::function<void()>> calls;
    std::mutex mutex;
    std::condition_variable init_cond;
    bool initialized;

    static void run_all_asyncs(uv_async_t *_async)
    {
        NativeCallQueue *self = (NativeCallQueue*)(((char*)_async)-offsetof(NativeCallQueue, async));

        std::list<std::function<void()>> tmp;
        {
            std::unique_lock<std::mutex> lock(self->mutex);
            tmp = std::move(self->calls);
            self->calls.clear();
        }

        for (auto& fn : tmp)
        {
            fn();
        }
    }

public:
    void Init()
    {
        std::unique_lock<std::mutex> lock(mutex);
        uv_async_init(uv_default_loop(), &async, run_all_asyncs);
        initialized = true;
        init_cond.notify_all();
    }

    template<class Callback>
    void InvokeAsync(Callback&& call)
    {
        std::unique_lock<std::mutex> lock(mutex);
        while (!initialized) init_cond.wait(lock);
        calls.emplace_back(std::forward<Callback>(call));
        uv_async_send(&async);
    }
};


class JavaInvoker
{
private:
    JNIEnv *env;
    jmethodID invoke;
    jobject executor;
    jmethodID executor_execute;
    jclass JavaCallback;
    jclass NodeJSLauncher;
    jmethodID NodeJSLauncher_invokeAsync;
    std::mutex method_lock;
    std::unordered_map<std::u16string, jobject> methods;
    std::unordered_map<jlong, v8::Global<v8::Promise::Resolver>> callbacks;
    jlong next_promise_id = 1;

public:
    void Init(JNIEnv *env, jobject jClassLoader)
    {
        this->env = env;

        jclass ClassLoader = env->FindClass("java/lang/ClassLoader");
        jmethodID loadClass = env->GetMethodID(ClassLoader, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;");
        JavaCallback = (jclass)env->NewGlobalRef(env->CallObjectMethod(jClassLoader, loadClass, env->NewStringUTF("edu/stanford/thingengine/nodejs/JavaCallback"), (jboolean)true));
        NodeJSLauncher = (jclass)env->NewGlobalRef(env->CallObjectMethod(jClassLoader, loadClass, env->NewStringUTF("edu/stanford/thingengine/nodejs/NodeJSLauncher"), (jboolean)true));
        env->DeleteGlobalRef(jClassLoader);

        this->invoke = env->GetMethodID(JavaCallback, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;");

        jclass Executors = env->FindClass("java/util/concurrent/Executors");

        jobject executor = env->CallStaticObjectMethod(Executors, env->GetStaticMethodID(Executors, "newCachedThreadPool", "()Ljava/util/concurrent/ExecutorService;"));
        this->executor = env->NewGlobalRef(executor);
        NodeJSLauncher_invokeAsync = env->GetStaticMethodID(NodeJSLauncher, "asyncCallback", "(Ljava/util/concurrent/ExecutorService;JLedu/stanford/thingengine/nodejs/JavaCallback;[Ljava/lang/Object;)V");

        if (env->ExceptionOccurred())
            throw std::logic_error("initialization failed");
    }

    void Deinit()
    {
        std::unique_lock<std::mutex> lock(method_lock);

        env->DeleteGlobalRef(executor);
        env->DeleteGlobalRef(NodeJSLauncher);
        env->DeleteGlobalRef(JavaCallback);
        for (const auto& it : methods) {
            env->DeleteGlobalRef(it.second);
        }
        methods.clear();
        callbacks.clear();
        executor = nullptr;
        env = nullptr;
    }

    InteropValue InvokeSync(Isolate *isolate, std::u16string&& function_name, std::vector<InteropValue>&& arguments)
    {
        jobject callback;
        {
            std::unique_lock<std::mutex> lock(method_lock);
            auto it = methods.find(function_name);
            if (it == methods.end()) {
                isolate->ThrowException(v8::Exception::TypeError(string_to_v8(isolate, u"no such function " + function_name)));
                return InteropValue();
            }

            callback = env->NewLocalRef(it->second);
        }

        jobjectArray args = env->NewObjectArray(arguments.size(), env->FindClass("java/lang/Object"), nullptr);
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
            isolate->ThrowException(v8::Exception::TypeError(OneByteString(isolate, "failed to marshal return value")));
            return InteropValue();
        }

        return ret_val;
    }

    Local<v8::Promise> InvokeAsync(Isolate *isolate, std::u16string&& function_name, std::vector<InteropValue>&& arguments)
    {
        Local<v8::Promise::Resolver> resolver = v8::Promise::Resolver::New(isolate);
        jobject callback;
        {
            std::unique_lock<std::mutex> lock(method_lock);
            auto it = methods.find(function_name);
            if (it == methods.end()) {
                resolver->Reject(v8::Exception::TypeError(string_to_v8(isolate, u"no such function " + function_name)));
                return resolver->GetPromise();
            }

            callback = env->NewLocalRef(it->second);
        }

        jobjectArray args = env->NewObjectArray(arguments.size(), env->FindClass("java/lang/Object"), nullptr);
        for (size_t i = 0; i < arguments.size(); i++) {
            env->SetObjectArrayElement(args, i, arguments[i].ToJava(env));
        }

        jlong promise_id = next_promise_id++;

        v8::Global<v8::Promise::Resolver> &slot = callbacks[promise_id];
        slot = v8::Global<v8::Promise::Resolver>(isolate, resolver);

        env->CallStaticVoidMethod(NodeJSLauncher, NodeJSLauncher_invokeAsync, executor, promise_id, callback, args);
        jthrowable exception = env->ExceptionOccurred();
        if (exception != nullptr) {
            env->ExceptionClear();
            Local<Value> v8_exception = java_exception_to_v8(isolate, env, exception);
            resolver->Reject(v8_exception);
            callbacks.erase(promise_id);
        }

        return resolver->GetPromise();
    }

    void CompletePromise(jlong promiseId, const v8::Maybe<std::u16string>& error_msg, const InteropValue& result)
    {
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

    void Register(std::u16string&& fn, jobject method)
    {
        std::unique_lock<std::mutex> lock(method_lock);
        methods[std::move(fn)] = method;
    }
};

static struct {
    CallQueue queue;
    NativeCallQueue native_queue;
    v8::Global<Object> launcher_module;
    bool terminate;

    JavaInvoker java_invoker;
} global_state;

static bool ShouldAbortOnUncaughtException(Isolate* isolate) {
    // FIXME for now we never abort for uncaught exceptions
    return false;
}

static void console_log(const FunctionCallbackInfo<Value>& args)
{
    String::Utf8Value msg(args[0]->ToString());
    log_info("nodejs", "%s", *msg);
    args.GetReturnValue().SetUndefined();
}

static void console_error(const FunctionCallbackInfo<Value>& args)
{
    String::Utf8Value msg(args[0]->ToString());
    log_error("nodejs", "%s", *msg);
    args.GetReturnValue().SetUndefined();
}

static void console_warn(const FunctionCallbackInfo<Value>& args)
{
    String::Utf8Value msg(args[0]->ToString());
    log_warn("nodejs", "%s", *msg);
    args.GetReturnValue().SetUndefined();
}

static void process_exit(const FunctionCallbackInfo<Value>& args)
{
    global_state.terminate = true;
}

static void set_async_receiver(const FunctionCallbackInfo<Value>& args)
{
    Local<v8::Function> receiver = args[0].As<v8::Function>();
    Local<v8::Object> this_obj = args[0].As<v8::Object>();
    global_state.queue.SetNodeReceiver(receiver, this_obj);
}

static void call_java_async(const FunctionCallbackInfo<Value>& args)
{
    Local<String> fn = args[0].As<String>();
    size_t first_arg = 1, end_arg = (size_t)args.Length();

    std::vector<InteropValue> arguments(end_arg - first_arg);
    for (size_t i = first_arg; i < end_arg; i++) {
        if (!arguments[i-first_arg].InitFromJS(args.GetIsolate(), args[i]))
            return;
    }

    args.GetReturnValue().Set(global_state.java_invoker.InvokeAsync(args.GetIsolate(), v8_to_string(fn), std::move(arguments)));
}

static void call_java_sync(const FunctionCallbackInfo<Value>& args)
{
    Local<String> fn = args[0].As<String>();
    size_t first_arg = 1, end_arg = (size_t)args.Length();

    std::vector<InteropValue> arguments(end_arg - first_arg);
    for (size_t i = first_arg; i < end_arg; i++) {
        if (!arguments[i-first_arg].InitFromJS(args.GetIsolate(), args[i]))
            return;
    }

    InteropValue ret_value = global_state.java_invoker.InvokeSync(args.GetIsolate(), v8_to_string(fn), std::move(arguments));
    if (ret_value.IsNull())
        args.GetReturnValue().SetNull();
    else
        args.GetReturnValue().Set(ret_value.ToJavaScript(args.GetIsolate()));
}

static void register_module(Local<Object> exports, Local<Value>, void *)
{
    global_state.launcher_module = v8::Global<Object>(exports->GetIsolate(), exports);

    NODE_SET_METHOD(exports, "log", console_log);
    NODE_SET_METHOD(exports, "error", console_error);
    NODE_SET_METHOD(exports, "warn", console_warn);
    NODE_SET_METHOD(exports, "exit", process_exit);
    NODE_SET_METHOD(exports, "setAsyncReceiver", set_async_receiver);
    NODE_SET_METHOD(exports, "callJavaAsync", call_java_async);
    NODE_SET_METHOD(exports, "callJavaSync", call_java_sync);
}

static node::node_module launcher_module = {
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


static int loop(node::Environment *env, Isolate *isolate, uv_loop_t *event_loop, v8::Platform *platform)
{
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
    virtual void* Allocate(size_t size) {
        return calloc(size, 1);
    }
    virtual void* AllocateUninitialized(size_t size) {
        return malloc(size);
    }
    virtual void Free(void* data, size_t) {
        free(data);
    }
};

static std::unique_ptr<char[]>
read_full_asset(AAsset *asset)
{
    off64_t length = AAsset_getLength64(asset);

    std::unique_ptr<char[]> buffer(new char[length]);
    off64_t off = 0;

    while (off < length) {
        int read = AAsset_read(asset, &buffer[off], (size_t)(length - off));
        off += read;
    }

    AAsset_close(asset);
    return buffer;
}

static void
start_node(JavaVM *vm, AAsset *app_code, jobject jClassLoader)
{
    static const char *argv[] = { "node", nullptr };
    int argc = 1;
    int exec_argc;
    const char** exec_argv;

    JNIEnv *jnienv;
    vm->AttachCurrentThread(&jnienv, nullptr);
    global_state.java_invoker.Init(jnienv, jClassLoader);

    node_module_register(&launcher_module);
    node::Init(&argc, const_cast<const char**>(argv), &exec_argc, &exec_argv);

    std::unique_ptr<v8::Platform> platform(v8::platform::CreateDefaultPlatform(0));
    V8::InitializePlatform(platform.get());
    V8::Initialize();

    log_info("nodejs", "nodejs sleeping, waiting for debugger... %d", getpid());
    //sleep(120);

    ArrayBufferAllocator array_buffer_allocator;
    Isolate::CreateParams params;
    params.array_buffer_allocator = &array_buffer_allocator;
    params.code_event_handler = nullptr;
    Isolate* isolate = Isolate::New(params);

    {
        Locker locker(isolate);
        Isolate::Scope isolate_scope(isolate);
        HandleScope handle_scope(isolate);
        Local<Context> context = Context::New(isolate);

        // where the magic happens!
        Context::Scope context_scope(context);
        node::Environment *env = node::CreateEnvironment(isolate, uv_default_loop(), context,
                                                         argc, argv, exec_argc, exec_argv);
        Local<Object> process = node::GetProcessObject(env);
        std::unique_ptr<char[]> code_buffer = read_full_asset(app_code);
        process->DefineOwnProperty(context, OneByteString(isolate, "_eval"), OneByteString(isolate, code_buffer.get()),
                           v8::PropertyAttribute::ReadOnly).FromJust();
        code_buffer = nullptr;
        node::LoadEnvironment(env);
        //assert(!global_state.launcher_module.IsEmpty());
        global_state.queue.Init(isolate);
        global_state.native_queue.Init();

        isolate->SetAbortOnUncaughtExceptionCallback(ShouldAbortOnUncaughtException);
        log_info("nodejs", "NodeJS initialized");

        int exit_code = loop(env, isolate, uv_default_loop(), platform.get());
        log_info("nodejs", "NodeJS code exited with code %d", exit_code);

        FreeEnvironment(env);
    }

    isolate->Dispose();
    V8::Dispose();
    delete[] exec_argv;

    global_state.java_invoker.Deinit();
    vm->DetachCurrentThread();
}

static bool
make_args(std::vector<InteropValue>& vector, JNIEnv *env, jobjectArray args)
{
    vector.resize((size_t)env->GetArrayLength(args));
    for (size_t i = 0; i < vector.size(); i++){
        if (!vector[i].InitFromJava(env, env->GetObjectArrayElement(args, i), true))
            return false;
    }
    return true;
}

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

    global_state.native_queue.InvokeAsync(std::bind(&JavaInvoker::CompletePromise, std::ref(global_state.java_invoker), promiseId, std::move(error_msg), std::move(value)));
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
    } catch (js_exception &js) {
        jclass RuntimeException = env->FindClass("java/lang/RuntimeException");
        jobject e = env->NewObject(RuntimeException, env->GetMethodID(RuntimeException, "<init>",
                                                                      "(Ljava/lang/String;)V"),
                                   string_to_jstring(env, js.message));
        env->Throw((jthrowable) e);
        return nullptr;
    } catch (std::exception &e) {
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