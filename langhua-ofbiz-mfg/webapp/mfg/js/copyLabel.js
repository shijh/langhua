/*
 * Copy to clipboard helper of the hardware assembly component.
 *
 * The button sits next to a readonly textarea holding the text to copy, so the button finds the
 * text through its parent element. navigator.clipboard is used when available (needs a secure
 * context, which the OFBiz HTTPS port provides), with document.execCommand as a fallback.
 *
 * Kept in a static file instead of an inline <script> so a future enforcing
 * Content-Security-Policy (default-src 'self') does not break it.
 */
function mfgCopyLabel(button) {
    var holder = button.parentNode;
    var box = holder ? holder.querySelector('textarea') : null;
    if (!box) {
        return;
    }
    var original = button.innerHTML;
    var done = function () {
        button.innerHTML = '已复制';
        button.disabled = true;
        window.setTimeout(function () {
            button.innerHTML = original;
            button.disabled = false;
        }, 1500);
    };
    var fallback = function () {
        box.focus();
        box.select();
        try {
            if (document.execCommand('copy')) {
                done();
            } else {
                window.alert('复制失败，请手动全选复制');
            }
        } catch (e) {
            window.alert('复制失败，请手动全选复制');
        }
    };
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(box.value).then(done, fallback);
    } else {
        fallback();
    }
}
