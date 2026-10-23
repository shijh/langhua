/*
 * Keeps a mouse that likes to double click from sending the same thing twice.
 *
 * Every form is submitted at most once per window: the second submit of a double click is dropped
 * before the page handler sees it, and the buttons of the form are dimmed so the person can tell
 * the click went through. Buttons that do their own request (a screenshot scan) and the links that
 * unfold a row are guarded the same way - a double click on "3 ▸" would open and close it again.
 */
(function () {
    'use strict';
    var SUBMIT_MS = 1200;
    var CLICK_MS = 600;

    /** true when the element was not touched within the window; stamps it otherwise */
    function allowed(element, windowMs) {
        var now = Date.now();
        var last = parseInt(element.getAttribute('data-last-action') || '0', 10);
        if (now - last < windowMs) {
            return false;
        }
        element.setAttribute('data-last-action', String(now));
        return true;
    }

    function dim(form, on) {
        var items = form.querySelectorAll('input[type="submit"], button');
        for (var i = 0; i < items.length; i++) {
            if (on) {
                if (items[i].className.indexOf('busy') < 0) {
                    items[i].className = (items[i].className + ' busy').replace(/^\s+/, '');
                }
            } else {
                items[i].className = items[i].className.replace(/\s*\bbusy\b/g, '');
            }
        }
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form || !form.tagName || form.tagName.toUpperCase() !== 'FORM') {
            return;
        }
        if (!allowed(form, SUBMIT_MS)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            return;
        }
        dim(form, true);
        setTimeout(function () {
            dim(form, false);
        }, SUBMIT_MS);
    }, true);

    document.addEventListener('click', function (event) {
        var node = event.target;
        while (node && node.getAttribute) {
            var guarded = node.getAttribute('data-once') === 'Y'
                || node.hasAttribute('data-toggle-lines')
                || node.hasAttribute('data-toggle-receipt');
            if (guarded) {
                if (!allowed(node, CLICK_MS)) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
                return;
            }
            node = node.parentNode;
        }
    }, true);
})();
