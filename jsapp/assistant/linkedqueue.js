// -*- mode: js; indent-tabs-mode: nil; js-basic-offset: 4 -*-
//
// This file is part of ThingEngine
//
// Copyright 2016 Giovanni Campagna <gcampagn@cs.stanford.edu>
//
// See COPYING for details
"use strict";

class LinkedList {
    constructor(data) {
        this.data = data;
        this.next = null;
    }
}

class ListForwardIterator {
    constructor(node) {
        this.next = node;
    }

    next() {
        if (this.next === null) {
            return { done: true };
        } else {
            var curr = this.next;
            this.next = curr.next;
            return { done: false, value: curr.data };
        }
    }
}

module.exports = class LinkedQueue {
    constructor() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    isEmpty() {
        return this.head === null;
    }

    [Symbol.iterator]() {
        return new ListForwardIterator(this.head);
    }

    push(data) {
        if (this.tail === null) {
            this.head = this.tail = new LinkedList(data);
        } else {
            this.tail.next = new LinkedList(data);
            this.tail = this.tail.next;
        }
        this.size ++;
    }

    shift() {
        if (this.head === null)
            throw new Error('Empty queue');
        var node = this.head;
        this.head = this.head.next;
        if (this.head === null)
            this.tail = null;
        this.size --;
        return node.data;
    }

    peekOldest() {
        if (this.head === null)
            return null;
        return this.head.data;
    }

    peekNewest() {
        if (this.tail === null)
            return null;
        return this.tail.data;
    }
}
