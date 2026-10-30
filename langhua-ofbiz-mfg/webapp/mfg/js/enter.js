/*
 * This library is part of OFBiz-MFG component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 */

/* One key starts the thing that is waiting (Step 186).
 *
 * A block that has just taken a file - from the clipboard, from a drop, from the picker - says so
 * here, and from then on a bare Enter starts that block wherever the focus happens to be. After a
 * Ctrl+V a person's hand is still on the keyboard, and reaching for the mouse is the slow part.
 *
 * Only one block owns the key at a time, so pasting a screenshot and then a workbook cannot start
 * both on one key press. The key is not taken while the person is typing (Enter in a box is a new
 * line, or the form's own submit), nor from a modified Enter (Shift+Enter is still a new line), nor
 * when the button itself has the focus (the browser clicks a focused button on Enter by itself, and
 * doing it here as well would start the same thing twice).
 *
 * Usage, from the page that offers the paste:
 *     window.mfgAskEnter('scan', scanButton, recognize);   // a file is waiting: Enter starts it
 *     window.mfgEnterDone('scan');                        // it started: Enter must not start it twice
 *     window.mfgEnterWaiting('scan')                      // optional: is my block the one waiting?
 */
(function () {
    var owner = null;
    var button = null;
    var start = null;

    function askEnter(name, itsButton, itsStart) {
        if (!itsStart) {
            return;
        }
        owner = name;
        button = itsButton || null;
        start = itsStart;
    }

    function enterDone(name) {
        if (owner === name) {
            owner = null;
            button = null;
            start = null;
        }
    }

    function enterWaiting(name) {
        return !!start && owner === name;
    }

    document.addEventListener('keydown', function (event) {
        if (!start) {
            return;
        }
        if (event.key !== 'Enter' && event.keyCode !== 13) {
            return;
        }
        if (event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) {
            return;
        }
        var node = event.target;
        var tag = node && node.tagName ? node.tagName.toLowerCase() : '';
        if (tag === 'input' || tag === 'textarea' || tag === 'select') {
            return;
        }
        if (node === button) {
            return;
        }
        event.preventDefault();
        var run = start;
        var who = owner;
        /* the key is used up before the block runs: an answer that comes back slowly must not let a
           second Enter start the same thing again */
        enterDone(who);
        run();
    });

    window.mfgAskEnter = askEnter;
    window.mfgEnterDone = enterDone;
    window.mfgEnterWaiting = enterWaiting;
}());
