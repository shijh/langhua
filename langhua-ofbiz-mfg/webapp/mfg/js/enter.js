/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
