<#ftl encoding="UTF-8">
<#--
  Shared helper for the "copy to clipboard" buttons.
  Every button sits next to a readonly textarea holding the text to copy, so the button finds it
  through its parent element. Falls back to document.execCommand for older engines.
-->
<script type="text/javascript">
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
</script>
