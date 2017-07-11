//
// Created by gcampagn on 7/10/17.
//

#ifndef THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_UTILS_H
#define THINGENGINE_PLATFORM_ANDROID_NODE_LAUNCHER_UTILS_H

#include <string>
#include <cstdio>
#include <cstdlib>

#include <jni.h>
#include "node/node.h"
#include "node/uv.h"
#include "node/libplatform/libplatform.h"
#include "node/node_buffer.h"

namespace thingengine_node_launcher {

std::u16string jstring_to_string(JNIEnv *env, jstring str);
std::u16string jobject_to_string(JNIEnv *env, jobject obj);
jstring string_to_jstring(JNIEnv *env, const std::u16string &str);

v8::Local<v8::String> string_to_v8(v8::Isolate *isolate, const std::u16string &s);
std::basic_string<char16_t> v8_to_string(const v8::Local<v8::String> &s);

v8::Local<v8::Value> java_exception_to_v8(v8::Isolate *isolate, JNIEnv *env, jthrowable throwable);
v8::Local<v8::Value> exception_to_v8(v8::Isolate *isolate, const std::u16string &error_msg);

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
