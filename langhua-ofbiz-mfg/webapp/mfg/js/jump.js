/*
 * Put a block (or one row of it) just under the sticky nav, without guessing.
 *
 * `element.scrollIntoView({block: 'center'})` looks right on a short block, but the blocks on the
 * receipt page grow: once the order block carries the checklist of a pasted screenshot, or the
 * workbook block carries the list of orders it could not book in, the block is several screens
 * tall - centering it leaves its first line (the file box and the button the person needs next)
 * somewhere above the viewport. So aim the element's own top at nav height + a small gap, and
 * leave the page alone when it is already in the comfortable band.
 *
 * Kept in one file because three places want it: pasting a screenshot, pasting the exported
 * workbook, and jumping to a receipt after clicking its number in the list.
 */
(function () {
    function navHeight() {
        var nav = document.querySelector('.nav');
        return nav ? nav.getBoundingClientRect().height : 0;
    }

    function jumpUnderNav(element, gap) {
        if (!element || !element.getBoundingClientRect) {
            return;
        }
        var top = element.getBoundingClientRect().top;
        var offset = navHeight() + (gap || 14);
        // already under the nav and in the upper half of the screen: nothing to fix
        if (top >= offset && top <= window.innerHeight * 0.45) {
            return;
        }
        var pageTop = top + (window.scrollY || window.pageYOffset || 0);
        var lowest = document.documentElement.scrollHeight - window.innerHeight;
        var target = Math.max(0, pageTop - offset);
        window.scrollTo(0, lowest > 0 ? Math.min(target, lowest) : target);
    }

    window.jumpUnderNav = jumpUnderNav;
}());
