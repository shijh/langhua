/*
 * Dashboard auto refresh.
 *
 * The page is plain server rendered HTML, so the simplest reliable refresh is a reload: it keeps
 * the URL (and any filter in it) and needs no JSON endpoint, no chart library and no build step.
 */
(function () {
    var RELOAD_SECONDS = 60;

    var hint = document.getElementById('refresh-hint');
    var left = RELOAD_SECONDS;

    function tick() {
        left -= 1;
        if (hint) {
            hint.textContent = '页面每 ' + RELOAD_SECONDS + ' 秒自动刷新（' + left + ' 秒后）';
        }
        if (left <= 0) {
            window.location.reload();
            return;
        }
        window.setTimeout(tick, 1000);
    }

    if (hint) {
        hint.textContent = '页面每 ' + RELOAD_SECONDS + ' 秒自动刷新（' + left + ' 秒后）';
    }
    window.setTimeout(tick, 1000);
})();
