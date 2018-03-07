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

#include "node-launcher-utils.hpp"

using namespace v8;

namespace thingengine_node_launcher {

std::u16string
jstring_to_string(JNIEnv *env, jstring str) {
    jboolean is_copy;
    const jchar *jstr = env->GetStringChars(str, &is_copy);
    std::u16string s((char16_t *) jstr, (size_t) env->GetStringLength(str));
    env->ReleaseStringChars(str, jstr);
    return s;
}

std::u16string
jobject_to_string(JNIEnv *env, jobject obj) {
    jclass Object = env->FindClass("java/lang/Object");
    jmethodID toString = env->GetMethodID(Object, "toString", "()Ljava/lang/String;");
    jstring str = (jstring) env->CallObjectMethod(obj, toString);
    return jstring_to_string(env, str);
}

jstring
string_to_jstring(JNIEnv *env, const std::u16string &str) {
    return env->NewString((const jchar *) str.data(), str.length());
}

Local <String>
string_to_v8(Isolate *isolate, const std::u16string &s) {
    return v8::String::NewFromTwoByte(isolate, (const uint16_t *) s.data(),
                                      v8::String::NewStringType::kNormalString, s.length());
}

std::basic_string<char16_t>
v8_to_string(const Local <v8::String> &s) {
    int length = s->Length();
    char16_t *buffer = new char16_t[length];

    s->Write((uint16_t *) buffer, 0, -1, v8::String::NO_NULL_TERMINATION);
    std::basic_string<char16_t> stdstring = std::basic_string<char16_t>(buffer,
                                                                        (size_t) length);
    delete[] buffer;
    return stdstring;
}

v8::Local <v8::Value>
java_exception_to_v8(Isolate *isolate, JNIEnv *env, jthrowable throwable) {
    jclass Throwable = env->FindClass("java/lang/Throwable");
    jmethodID getMessage = env->GetMethodID(Throwable, "getMessage", "()Ljava/lang/String;");
    jstring str = (jstring) env->CallObjectMethod(throwable, getMessage);

    jsize str_len = env->GetStringLength(str);
    const jchar *str_chars = env->GetStringChars(str, nullptr);
    Local <String> v8_message = v8::String::NewFromTwoByte(isolate, str_chars,
                                                           v8::String::NewStringType::kNormalString,
                                                           str_len);
    env->ReleaseStringChars(str, str_chars);

    return v8::Exception::Error(v8_message);
}

v8::Local <v8::Value>
exception_to_v8(Isolate *isolate, const std::u16string &error_msg) {
    Local <String> v8_message = v8::String::NewFromTwoByte(isolate,
                                                           (uint16_t *) error_msg.data(),
                                                           v8::String::NewStringType::kNormalString,
                                                           error_msg.size());
    return v8::Exception::Error(v8_message);
}

v8::Local <v8::Value>
exception_to_v8(Isolate *isolate, const char *error_msg) {
    Local <String> v8_message = v8::String::NewFromOneByte(isolate, (uint8_t *) error_msg);
    return v8::Exception::Error(v8_message);
}

}