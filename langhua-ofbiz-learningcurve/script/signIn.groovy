/*
 * Everything the sign-in page needs from Java (Step 123).
 *
 * 1. The messages of a refused login. The framework answers in the language of the session, which
 *    on these instances is English ("following error occurred during login: Password incorrect.").
 *    The people who log in here are on the shop floor, so the three cases that really happen are
 *    translated; anything else is shown as it came.
 *
 * 2. The CSRF token of the form. A stock OFBiz ships with the defence switched off (an empty
 *    csrf.defense.strategy, so NoCsrfDefenseStrategy answers null and the page renders no hidden
 *    field). If an instance turns org.apache.ofbiz.security.CsrfDefenseStrategy on, the same page
 *    starts carrying a real token and keeps working.
 *
 * Both jobs are guarded: a message or a token that cannot be made must never keep a person out of
 * the login page.
 */
try {
    Map<String, String> known = [
        'Password incorrect.' : '密码不对，再试一次（注意大小写）',
        'User not found.'     : '没有这个账号名，检查一下拼写'
    ]
    List<String> messages = []
    for (Object raw : (context.errorMessageList ?: [])) {
        String text = String.valueOf(raw)
        // the framework wraps the reason: "following error occurred during login: <reason>"
        int cut = text.lastIndexOf(': ')
        String reason = (cut >= 0 ? text.substring(cut + 2) : text).trim()
        if (reason.contains('has been disabled') || reason.startsWith('User account is disabled')) {
            // the account is locked after too many wrong passwords; say when it opens again
            def clock = (reason =~ /will be re-enabled \S+ (\d\d:\d\d:\d\d)/)
            String when = clock.find() ? ('，' + clock.group(1) + ' 之后自动恢复') : '，过几分钟会自动恢复'
            messages.add('连着输错密码，账号被临时锁住了' + when)
        } else {
            messages.add(known[reason] ?: text)
        }
    }
    context.loginErrors = messages
} catch (Throwable ignored) {
    context.loginErrors = null
}

try {
    context.csrfName = org.apache.ofbiz.security.CsrfUtil.getTokenNameNonAjax()
    String target = context.csrfTarget ?: 'login'
    context.csrfValue = org.apache.ofbiz.security.CsrfUtil.generateTokenForNonAjax(request, target)
} catch (Throwable ignored) {
    context.csrfName = null
    context.csrfValue = null
}
