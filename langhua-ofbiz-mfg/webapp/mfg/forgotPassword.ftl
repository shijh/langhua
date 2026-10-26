<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  "Forgot your password" page (Step 123).

  The stock page shows a white Apache table with two English buttons. This one has the same job in
  the shop floor look, and it offers only one of them: the password hint. None of the instances has
  a mail server configured, so the E-mail button could never do more than answer "unable to email
  password" - the hint, plus "ask the administrator to reset it", is what actually helps here. If
  mail is configured one day, add a second submit button named EMAIL_PASSWORD (the framework's
  LoginEvents already understands it).

  The form posts to the framework's own forgotPassword request (USERNAME + GET_PASSWORD_HINT), which
  answers on this same view: signIn.groovy turns its English answer into Chinese.

  Context: loginUser (what was typed), forgotMessage / forgotIsHint (from signIn.groovy),
  csrfName / csrfValue (empty unless the instance turns the CSRF defence on).
-->
<#assign typed = (loginUser)!''>
<#assign note = (forgotMessage)!''>
<#assign isHint = ((forgotIsHint)!'') == 'Y'>
<#assign csrfField = (csrfName)!''>
<#assign csrfToken = (csrfValue)!''>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>找回密码 - 花石箓 · 硬件组装 MES</title>
    <link rel="icon" type="image/png" sizes="32x32" href="/mfg/images/logo-32.png"/>
    <link rel="icon" type="image/png" sizes="64x64" href="/mfg/images/logo-64.png"/>
    <link rel="apple-touch-icon" href="/mfg/images/logo-180.png"/>
    <link rel="stylesheet" type="text/css" href="/mfg/css/mfg.css?v=4"/>
</head>
<body class="signin">
<main class="signin-card">
    <img class="signin-logo" src="/mfg/images/logo-180.png" alt="" width="72" height="72"/>
    <h1>找回密码</h1>
    <p class="signin-sub">填账号名看看当初留下的密码提示</p>
<#if note?has_content>
    <p class="<#if isHint>signin-note<#else>signin-error</#if>">${note}</p>
</#if>
    <form method="post" action="/mfg/control/forgotPassword">
        <label class="signin-field">账号
            <input type="text" name="USERNAME" value="${typed}" autocomplete="username"
                   autocapitalize="off" spellcheck="false" autofocus required/>
        </label>
<#if csrfField?has_content && csrfToken?has_content>
        <input type="hidden" name="${csrfField}" value="${csrfToken}"/>
</#if>
        <button type="submit" name="GET_PASSWORD_HINT" value="Y">查看密码提示</button>
    </form>
    <p class="signin-hint">这台机器没有配邮件服务器，所以不能把密码寄到邮箱；提示也想不起来，请让管理员在「用户账号」页
        把密码重置回出厂值，再用新密码登录。</p>
    <p class="signin-actions"><a class="button secondary" href="/mfg/control/login">返回登录</a></p>
</main>
<p class="signin-foot">© 2026 天津朗华博基科技发展有限公司</p>
</body>
</html>
