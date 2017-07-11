// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// Copyright 2017 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See LICENSE for details
"use strict";

const native = process._linkedBinding('cvc4');
const smt = require('smtlib');

module.exports = class Solver extends smt.BaseSolver {
    constructor(logic) {
        super(logic);
        this.setOption('strings-exp');
        this.setOption('strings-guess-model');
    }

    checkSat() {
        this.add(smt.CheckSat());

        let stmts = [];
        this.forEachStatement((s) => stmts.push(s.toString()));

        let now = new Date;
        return native.solve(stmts.join('\n'), this.withAssignments, this.timeLimit).then((result) => {
            console.log('SMT elapsed time: ' + ((new Date).getTime() - now.getTime()));

            let sat = undefined;
            let assignment = {};
            let cidx = 0;
            let constants = {};
            result.split('\n').forEach((line) => {
                //console.log('SMT:', line);
                if (line === 'sat') {
                    sat = true;
                    return;
                }
                if (line === 'unsat') {
                    sat = false;
                    return;
                }
                if (line === 'unknown') {
                    sat = true;
                    console.error('SMT TIMED OUT');
                    this.dump();
                    return;
                }
                if (line.startsWith('(error')) {
                    throw new Error('SMT error: ' + line);
                }

                const CONSTANT_REGEX = /; rep: @uc_([A-Za-z0-9_]+)$/;
                let match = CONSTANT_REGEX.exec(line);
                if (match !== null) {
                    constants[match[1]] = cidx++;
                    return;
                }
                const ASSIGN_CONST_REGEX = /\(define-fun ([A-Za-z0-9_.]+) \(\) ([A-Za-z0-9_]+) @uc_([A-Za-z0-9_]+)\)$/
                match = ASSIGN_CONST_REGEX.exec(line);
                if (match !== null) {
                    assignment[match[1]] = constants[match[3]];
                    return;
                }

                const ASSIGN_BOOL_REGEX = /\(define-fun ([A-Za-z0-9_.]+) \(\) Bool (true|false)\)$/;
                match = ASSIGN_BOOL_REGEX.exec(line);
                if (match !== null) {
                    assignment[match[1]] = (match[2] === 'true');
                    return;
                }
            });

            if (sat)
                return [true, assignment];
            else
                return [false, undefined];
        });
    }
};
