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

#ifndef THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_MARSHAL_H
#define THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_MARSHAL_H

#include <future>
#include <type_traits>
#include <string>
#include <cstdlib>

#include "node-launcher-utils.hpp"

namespace thingengine_node_launcher {

class ByteBuffer
{
private:
    uint8_t *data;
    size_t len;

public:
    ByteBuffer() : data(nullptr), len(0) {}
    ~ByteBuffer();

    ByteBuffer(const ByteBuffer& other);
    ByteBuffer(ByteBuffer&& other)
    {
        data = other.data;
        other.data = nullptr;
        len = other.len;
        other.len = 0;
    }

    ByteBuffer(v8::Isolate *isolate, const v8::Local<v8::Object>& node_buffer);
    ByteBuffer(JNIEnv *env, jbyteArray byteArray);

    v8::Local<v8::Object> ToJavaScript(v8::Isolate *isolate) const;
    jbyteArray ToJava(JNIEnv *env) const;
};

class InteropValue {
    enum class Type { empty, boolean, string, number, json, buffer, null };

private:
    union Storage {
        bool b;
        std::aligned_storage<sizeof(std::u16string), alignof(std::u16string)>::type s;
        double n;
        std::aligned_storage<sizeof(ByteBuffer), alignof(ByteBuffer)>::type buf_space;
    };
    Storage storage;
    Type type;

    std::u16string& string() {
        assert (type == Type::json || type == Type::string);
        return reinterpret_cast<std::u16string&>(storage.s);
    }
    const std::u16string& string() const {
        assert (type == Type::json || type == Type::string);
        return reinterpret_cast<const std::u16string&>(storage.s);
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
    explicit InteropValue(std::nullptr_t) : type(Type::null) {}

    InteropValue(const InteropValue& v) : type(v.type)
    {
        if (type == Type::string || type == Type::json)
            new (&storage.s) std::u16string(v.string());
        else if (type == Type::buffer)
            new (&storage.buf_space) ByteBuffer(v.buf());
        else
            storage = v.storage;
    }

    InteropValue(InteropValue&& v) : type(v.type)
    {
        if (type == Type::string || type == Type::json)
            new (&storage.s) std::u16string(std::move(v.string()));
        else if (type == Type::buffer)
            new (&storage.buf_space) ByteBuffer(std::move(v.buf()));
        else
            storage = v.storage;
    }

    ~InteropValue()
    {
        using std::u16string;

        if (type == Type::string || type == Type::json)
            string().~u16string();
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

    bool InitFromJS(v8::Isolate *isolate, v8::Local<v8::Value> value);

    bool InitFromJava(JNIEnv *env, jobject obj, bool do_throw = true);

    v8::Local<v8::Value> ToJavaScript(v8::Isolate *isolate) const;
    jobject ToJava(JNIEnv *env) const;
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

    v8::Local<v8::String> GetFunctionName(v8::Isolate *isolate) const
    {
        return string_to_v8(isolate, m_fn);
    }

    v8::Local<v8::Array> ArgumentsToJS(v8::Isolate *isolate) const;

    void SetReturn(v8::Isolate *isolate, v8::Local<v8::Value> value);
    void SetException(v8::Isolate *isolate, v8::Local<v8::Value> value);

    static void then_promise(const v8::FunctionCallbackInfo<v8::Value>& info);
    static void catch_promise(const v8::FunctionCallbackInfo<v8::Value>& info);

    static v8::Global<v8::ObjectTemplate> tmpl_g;

    v8::Local<v8::Object> make_promise_holder(v8::Isolate *isolate, void *data);

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

    void Invoke(v8::Isolate *isolate, const v8::Local<v8::Function>& receiver, const v8::Local<v8::Object>& this_obj);
};

}

#endif //THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_MARSHAL_H
