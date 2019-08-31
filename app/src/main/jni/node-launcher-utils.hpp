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

#ifndef THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_UTILS_H
#define THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_UTILS_H

#include <string>
#include <cstdio>
#include <cstdlib>

#include <jni.h>
#include <node.h>
#include <uv.h>
#include <libplatform/libplatform.h>
#include <node_buffer.h>

namespace thingengine_node_launcher {

std::u16string jstring_to_string(JNIEnv *env, jstring str);
std::u16string jobject_to_string(JNIEnv *env, jobject obj);
jstring string_to_jstring(JNIEnv *env, const std::u16string &str);

v8::Local<v8::String> string_to_v8(v8::Isolate *isolate, const std::u16string &s);
std::basic_string<char16_t> v8_to_string(const v8::Local<v8::String> &s);
std::basic_string<char> v8_to_utf8(const v8::Local<v8::String> &s);

v8::Local<v8::Value> java_exception_to_v8(v8::Isolate *isolate, JNIEnv *env, jthrowable throwable);
v8::Local<v8::Value> exception_to_v8(v8::Isolate *isolate, const std::u16string &error_msg);
v8::Local <v8::Value> exception_to_v8(v8::Isolate *isolate, const char *error_msg);

inline v8::Local<v8::String> OneByteString(v8::Isolate *isolate,
                                           const char *data,
                                           int length) {
    return v8::String::NewFromOneByte(isolate,
                                      reinterpret_cast<const uint8_t *>(data),
                                      v8::NewStringType::kNormal,
                                      length).ToLocalChecked();
}

inline v8::Local<v8::String> OneByteString(v8::Isolate *isolate,
                                           const char *data) {
    return OneByteString(isolate, data, strlen(data));
}

}

#endif //THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_UTILS_H
