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

#include <future>
#include <string>
#include <type_traits>
#include <stdio.h>

#include <node/node.h>
#include <node/uv.h>

#include <cvc4/cvc4.h>
#undef As

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

namespace node_cvc4 {

inline Local <String>
to_javascript(Isolate *isolate, const std::string &s) {
    return v8::String::NewFromOneByte(isolate, (const uint8_t *) s.data(),
                                      v8::String::NewStringType::kNormalString, s.length());
}
std::string
v8_to_string(const Local <v8::String> &s) {
    int length = s->Utf8Length();
    char *buffer = new char[length];

    s->WriteUtf8(buffer, -1, nullptr, v8::String::NO_NULL_TERMINATION);
    std::string stdstring = std::string(buffer, (size_t) length);
    delete[] buffer;
    return stdstring;
}

v8::Local <v8::Value>
exception_to_v8(Isolate *isolate, const char *error_msg) {
    Local <String> v8_message = v8::String::NewFromOneByte(isolate, (uint8_t *) error_msg);
    return v8::Exception::Error(v8_message);
}

template<typename T>
class UVAsyncCall : private uv_work_t {
private:
    using function_type = T();
    std::packaged_task<function_type> task;
    std::future<T> future;
    v8::Isolate *isolate;
    v8::Global<v8::Promise::Resolver> js_promise;

    template<typename Callable>
    UVAsyncCall(v8::Isolate *_isolate, Callable&& _task) : task(std::forward<Callable>(_task)),
                                                           future(task.get_future()),
                                                           isolate(_isolate)
    {
        js_promise = v8::Global<v8::Promise::Resolver>(isolate, v8::Promise::Resolver::New(isolate));
    }

    static void do_work(uv_work_t* req) {
        UVAsyncCall<T> *self = static_cast<UVAsyncCall<T>*>(req);
        self->task();
    }

    static void do_after_work(uv_work_t* req, int status) {
        UVAsyncCall<T> *self = static_cast<UVAsyncCall<T>*>(req);
        {
            v8::Isolate *isolate = self->isolate;
            v8::HandleScope scope(isolate);
            v8::Local<v8::Promise::Resolver> promise(self->js_promise.Get(isolate));

            try {
                T value = self->future.get();
                promise->Resolve(to_javascript(isolate, value));
            } catch (std::exception &e) {
                promise->Reject(exception_to_v8(isolate, e.what()));
            }
        }
        delete self;
    }

    template<typename Callable>
    friend v8::Local<v8::Promise> Schedule(v8::Isolate *isolate, Callable&& _task);
};

template<typename Callable>
static v8::Local<v8::Promise> Schedule(v8::Isolate *isolate, Callable&& _task) {
    using result_type = decltype(std::forward<Callable>(_task)());
    UVAsyncCall<result_type> *req = new UVAsyncCall<result_type>(isolate, std::forward<Callable>(_task));
    v8::Local<v8::Promise::Resolver> resolver = req->js_promise.Get(isolate);
    v8::Local<v8::Promise> promise = resolver->GetPromise();

    uv_queue_work(uv_default_loop(), req, &UVAsyncCall<result_type>::do_work, &UVAsyncCall<result_type>::do_after_work);
    return promise;
}

struct solver_call
{
    std::string input;
    bool with_assignments;
    uint32_t time_limit;

    std::string operator()() const;
};

static void maybe_dump_models(CVC4::SmtEngine& engine, CVC4::Command* command, std::ostream & ostream)
{
    CVC4::Result res;
    CVC4::CheckSatCommand* cs = dynamic_cast<CVC4::CheckSatCommand*>(command);
    if(cs != NULL)
        res = cs->getResult();
    CVC4::QueryCommand* q = dynamic_cast<CVC4::QueryCommand*>(command);
    if(q != NULL)
        res = q->getResult();
    if (res.isNull())
        return;

    std::unique_ptr<CVC4::Command> c;
    if (res.asSatisfiabilityResult() == CVC4::Result::SAT ||
        (res.isUnknown() && res.whyUnknown() == CVC4::Result::INCOMPLETE)) {
        c.reset(new CVC4::GetModelCommand());
    }
    if (c)
        c->invoke(&engine, ostream);
}

std::string solver_call::operator()() const
{
    CVC4::ExprManager expr_manager;
    static std::mutex option_lock;
    char time_limit_opt[30];
    const char * opt_strings[5] = {
        "cvc4",
        "--lang", "smt",
        "--cpu-time",
        time_limit_opt
    };
    snprintf(time_limit_opt, sizeof(time_limit_opt), "--tlimit=%u", time_limit);

    CVC4::Options &options(const_cast<CVC4::Options &>(expr_manager.getOptions()));
    {
        std::lock_guard<std::mutex> guard(option_lock);
        CVC4::Options::parseOptions(&options, sizeof(opt_strings) / sizeof(opt_strings[0]),
                                    (char **) opt_strings);
    }

    CVC4::SmtEngine engine(&expr_manager);

    CVC4::parser::ParserBuilder parser_builder(&expr_manager, "<node-cvc4>", options);
    std::istringstream istream(input);
    std::ostringstream ostream;
    parser_builder
            .withInputLanguage(CVC4::language::input::LANG_SMTLIB_V2)
            .withIncludeFile(false)
            .withStreamInput(istream);

    std::unique_ptr<CVC4::parser::Parser> parser(parser_builder.build());

    while (!parser->done()) {
        std::unique_ptr<CVC4::Command> command(parser->nextCommand());
        if (command == nullptr)
            continue;
        command->invoke(&engine, ostream);
        if (with_assignments)
            maybe_dump_models(engine, command.get(), ostream);
    }

    return ostream.str();
}

static void solve(const FunctionCallbackInfo<Value>& args)
{
    v8::Isolate *isolate = args.GetIsolate();

    if (args.Length() != 3) {
        isolate->ThrowException(exception_to_v8(isolate, "Invalid number of arguments to .solve()"));
        return;
    }

    v8::MaybeLocal<v8::String> input = args[0]->ToString(isolate->GetCurrentContext());
    if (input.IsEmpty())
        return;
    v8::MaybeLocal<v8::Boolean> with_assignments = args[1]->ToBoolean(isolate->GetCurrentContext());
    if (with_assignments.IsEmpty())
        return;
    v8::MaybeLocal<v8::Uint32> time_limit = args[2]->ToUint32(isolate->GetCurrentContext());
    if (time_limit.IsEmpty())
        return;

    solver_call call { v8_to_string(input.ToLocalChecked()),
        with_assignments.ToLocalChecked()->Value(),
        time_limit.ToLocalChecked()->Value() };
    args.GetReturnValue().Set(Schedule(args.GetIsolate(), std::move(call)));
}

static void register_module(Local<Object> exports, Local<Value>, void *)
{
    NODE_SET_METHOD(exports, "solve", solve);
}

node::node_module module = {
        .nm_version = NODE_MODULE_VERSION,
        .nm_flags = 0,
        .nm_dso_handle = nullptr,
        .nm_filename = __FILE__,
        .nm_register_func = register_module,
        .nm_context_register_func = nullptr,
        .nm_modname = "cvc4",
        .nm_priv = nullptr,
        .nm_link = nullptr
};

}
