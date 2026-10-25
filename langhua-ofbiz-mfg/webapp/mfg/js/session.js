/* Reads a JSON answer, but if the session is gone it sends the person to the login page.
 *
 * OFBiz answers every auth="true" request with 401 + the login page HTML (checked: orderImage and
 * orderPeek both do). fetch(...).json() then throws, and the page would say "请求失败，再点一次"
 * for ever - the one thing that helps is logging in again. Loaded by decorator.ftl, so every page
 * can use it: fetch(url, ...).then(jsonOrLogin).then(...).catch(problem => {
 *     if (problem && problem.message === 'login') { return; }
 *     ... });
 */
window.jsonOrLogin = function (answer) {
    if (answer.redirected && /login/i.test(answer.url)) {
        window.location.replace(answer.url);
        return Promise.reject(new Error('login'));
    }
    if (answer.status === 401 || answer.status === 403) {
        window.location.replace('/mfg/control/login');
        return Promise.reject(new Error('login'));
    }
    return answer.text().then(function (text) {
        try {
            return JSON.parse(text);
        } catch (error) {
            /* a page came back instead of JSON: it can only be the login form */
            if (/name="USERNAME"|forgotPassword/i.test(text)) {
                window.location.replace('/mfg/control/login');
                return Promise.reject(new Error('login'));
            }
            throw error;
        }
    });
};
