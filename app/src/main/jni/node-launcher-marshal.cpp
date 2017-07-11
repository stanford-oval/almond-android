//
// Created by gcampagn on 7/10/17.
//

#include <cstdlib>
#include <android/log.h>

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

v8::Global<v8::ObjectTemplate> PackagedNodeCall::tmpl_g;

ByteBuffer::~ByteBuffer()
{
    std::free(data);
}

ByteBuffer::ByteBuffer(const ByteBuffer &other)
{
    data = (uint8_t*)malloc(other.len);
    len = other.len;
    if (data == nullptr)
        throw std::bad_alloc();
    memcpy(data, other.data, len);
}

ByteBuffer::ByteBuffer(v8::Isolate *isolate, const v8::Local<v8::Object> &node_buffer)
{
    len = node::Buffer::Length(node_buffer);
    data = (uint8_t*)malloc(len);
    memcpy(data, node::Buffer::Data(node_buffer), len);
}

ByteBuffer::ByteBuffer(JNIEnv *env, jbyteArray byteArray)
{
    jbyte *bytes;
    bytes = env->GetByteArrayElements(byteArray, nullptr);
    len = (size_t)env->GetArrayLength(byteArray);
    data = (uint8_t*)malloc(len);
    memcpy(data, bytes, len);
    env->ReleaseByteArrayElements(byteArray, bytes, JNI_ABORT);
}

v8::Local<v8::Object> ByteBuffer::ToJavaScript(v8::Isolate *isolate) const
{
    return node::Buffer::Copy(isolate, (char*)data, len).ToLocalChecked();
}

jbyteArray ByteBuffer::ToJava(JNIEnv *env) const
{
    jbyteArray array = env->NewByteArray(len);
    jbyte *bytes;
    bytes = env->GetByteArrayElements(array, nullptr);
    memcpy(bytes, data, len);
    env->ReleaseByteArrayElements(array, bytes, 0);
    return array;
}

bool InteropValue::InitFromJS(v8::Isolate *isolate, v8::Local<v8::Value> value)
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

bool InteropValue::InitFromJava(JNIEnv *env, jobject obj, bool do_throw)  {
    assert(type == Type::empty);
    if (obj == nullptr) {
        type = Type::null;
        return true;
    }

    jclass Number = env->FindClass("java/lang/Number");
    jclass String = env->FindClass("java/lang/String");
    jclass Boolean = env->FindClass("java/lang/Boolean");
    jclass JSONObject = env->FindClass("org/json/JSONObject");
    jclass JSONArray = env->FindClass("org/json/JSONArray");
    jclass byteArray = env->FindClass("[B");
    if (env->IsInstanceOf(obj, Number)) {
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

v8::Local<v8::Value> InteropValue::ToJavaScript(v8::Isolate *isolate) const {
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

jobject InteropValue::ToJava(JNIEnv *env) const  {
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
            return env->CallObjectMethod(jsonTokener, env->GetMethodID(JSONTokener, "nextValue", "()Ljava/lang/Object;"));
        }
    }
}

v8::Local<v8::Array> PackagedNodeCall::ArgumentsToJS(v8::Isolate *isolate) const
{
    v8::Local<v8::Array> arguments = v8::Array::New(isolate);
    uint32_t i = 0;
    for (const InteropValue& v : m_args) {
        arguments->Set(i++, v.ToJavaScript(isolate));
    }
    return arguments;
}

void PackagedNodeCall::SetReturn(v8::Isolate *isolate, v8::Local<v8::Value> value)
{
    if (!use_return)
        return;
    InteropValue ret;
    if (ret.InitFromJS(isolate, value))
        m_return.set_value(std::move(ret));
    else
        m_return.set_exception(std::make_exception_ptr(std::invalid_argument("argument cannot be marshalled")));
}

void PackagedNodeCall::SetException(v8::Isolate *isolate, v8::Local<v8::Value> value)
{
    Local<String> message = value->ToDetailString(isolate->GetCurrentContext()).ToLocalChecked();
    if (!use_return) {
        String::Utf8Value msg(message);
        log_error("nodejs", "Lost exception %s", *msg);
        return;
    }

    m_return.set_exception(std::make_exception_ptr(js_exception(v8_to_string(message))));
}

void PackagedNodeCall::then_promise(const v8::FunctionCallbackInfo<v8::Value> &info)
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

void PackagedNodeCall::catch_promise(const v8::FunctionCallbackInfo<v8::Value> &info) {
    if (info.Length() == 0)
        return;

    Local<Value> value = info[0];

    std::promise<InteropValue>* promise = static_cast<std::promise<InteropValue>*>(info.Data().As<Object>()->GetAlignedPointerFromInternalField(0));
    Local<String> message = value->ToDetailString(info.GetIsolate()->GetCurrentContext()).ToLocalChecked();
    if (promise == nullptr) {
        String::Utf8Value msg(message);
        log_error("nodejs", "Lost exception %s", *msg);
        return;
    }

    info.Data().As<Object>()->SetAlignedPointerInInternalField(0, nullptr);
    promise->set_exception(std::make_exception_ptr(js_exception(v8_to_string(message))));
    delete promise;
}

v8::Local<v8::Object> PackagedNodeCall::make_promise_holder(v8::Isolate *isolate, void *data) {
    Local<v8::ObjectTemplate> tmpl;
    if (tmpl_g.IsEmpty()) {
        tmpl = v8::ObjectTemplate::New(isolate);
        tmpl->SetInternalFieldCount(1);
        tmpl_g = v8::Global<v8::ObjectTemplate>(isolate, tmpl);
    } else {
        tmpl = tmpl_g.Get(isolate);
    }

    Local<Object> holder = tmpl->NewInstance();
    holder->SetAlignedPointerInInternalField(0, data);
    return holder;
}

void PackagedNodeCall::Invoke(v8::Isolate *isolate, const v8::Local<v8::Function> &receiver,
                              const v8::Local<v8::Object> &this_obj)
{
    Local<v8::String> fn = GetFunctionName(isolate);
    Local<v8::Array> args = ArgumentsToJS(isolate);

    Local<v8::Value> async_argv[2] = { fn, args };

    v8::TryCatch try_catch(isolate);
    Local<Value> ret_value = node::MakeCallback(isolate, this_obj, receiver, 2, async_argv);
    if (try_catch.HasCaught()) {
        SetException(isolate, try_catch.Exception());
    } else if (ret_value->IsPromise()) {
        if (use_return) {
            std::promise<InteropValue> *return_copy = new std::promise<InteropValue>(
                    std::move(m_return));
            Local<Object> holder = make_promise_holder(isolate, return_copy);
            Local<v8::Function> then = v8::Function::New(isolate, &then_promise, holder, 1);
            Local<v8::Function> _catch = v8::Function::New(isolate, &catch_promise, holder, 1);
            ret_value.As<v8::Promise>()->Then(then)->Catch(_catch);
        } else {
            Local<Object> holder = make_promise_holder(isolate, nullptr);
            Local<v8::Function> _catch = v8::Function::New(isolate, &catch_promise, holder, 1);
            ret_value.As<v8::Promise>()->Catch(_catch);
        }
    } else {
        SetReturn(isolate, ret_value);
    }
}

}