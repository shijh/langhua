/*
 * Unfolds the lines of a list row in place: a row with data-lines="<key>" holds the detail of the
 * row whose link carries data-toggle-lines="<key>". Used by the work order, product, delivery and
 * sales order lists.
 *
 * Folding is done with the "open" class, and mfg.css hides every tr[data-lines] that does not carry
 * it - so a block replaced by a fetch comes back folded without the script having to look at it.
 * The click is delegated from the document for the same reason: the <script> of an innerHTML block
 * never runs.
 */
(function () {
    'use strict';

    function toggle(link) {
        var key = link.getAttribute('data-toggle-lines');
        var open = link.getAttribute('data-open') !== 'Y';
        var rows = document.querySelectorAll('tr[data-lines="' + key + '"]');
        for (var i = 0; i < rows.length; i++) {
            if (open) {
                rows[i].className += ' open';
            } else {
                rows[i].className = rows[i].className.replace(/\s*\bopen\b/g, '');
            }
        }
        link.setAttribute('data-open', open ? 'Y' : 'N');
        var arrow = link.querySelector('span');
        if (arrow) {
            arrow.textContent = open ? '\u25be' : '\u25b8';
        }
    }

    document.addEventListener('click', function (event) {
        var node = event.target;
        while (node && node.getAttribute) {
            if (node.getAttribute('data-toggle-lines')) {
                event.preventDefault();
                toggle(node);
                return;
            }
            node = node.parentNode;
        }
    });
})();
