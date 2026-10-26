<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  "Please choose a password of your own" page (Step 123).

  OFBiz sends a login here while UserLogin.requirePasswordChange is Y (a fresh database, or an
  account an administrator changed by hand), and **the session is not logged in yet**: the gate is
  part of the login answer, not a page behind one.

  That is why the form posts to the login request again, with the account and both passwords:
  LoginWorker#login sees newPassword / newPasswordVerify, calls updatePassword with the password it
  just checked, and then either opens the MES (success) or asks once more. Anything else - a page
  behind auth="true", a service that needs a session - fails with "User authorization is required",
  which is how this page was first written.

  Context: loginUser (the account that was just used), csrfName / csrfValue (empty unless the
  instance turns the CSRF defence on), errorMessageList (of a refused login).
-->
<#assign typed = (loginUser)!''>
<#assign csrfField = (csrfName)!''>
<#assign csrfToken = (csrfValue)!''>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>修改密码 - 花石箓 · 硬件组装 MES</title>
    <link rel="icon" type="image/png" sizes="32x32" href="/mfg/images/logo-32.png"/>
    <link rel="icon" type="image/png" sizes="64x64" href="/mfg/images/logo-64.png"/>
    <link rel="apple-touch-icon" href="/mfg/images/logo-180.png"/>
    <link rel="stylesheet" type="text/css" href="/mfg/css/mfg.css?v=4"/>
</head>
<body class="signin">
<main class="signin-card signin-card-wide">
    <img class="signin-logo" src="/mfg/images/logo-180.png" alt="" width="72" height="72"/>
    <h1>请先修改密码</h1>
    <p class="signin-sub"><#if typed?has_content>账号 ${typed} 还在用出厂密码<#else>这个账号还在用出厂密码</#if>，改完才能继续用</p>
<#if ((errorMessageList)![])?has_content>
    <p class="signin-error"><#list errorMessageList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
    <form method="post" action="/mfg/control/login">
        <label class="signin-field">账号
            <input type="text" name="USERNAME" value="${typed}" autocomplete="username"
                   autocapitalize="off" spellcheck="false" required/>
        </label>
        <label class="signin-field">当前密码
            <input type="password" name="PASSWORD" autocomplete="current-password" autofocus required/>
        </label>
        <label class="signin-field">新密码
            <input type="password" name="newPassword" autocomplete="new-password" required/>
        </label>
        <label class="signin-field">再输一遍
            <input type="password" name="newPasswordVerify" autocomplete="new-password" required/>
        </label>
<#if csrfField?has_content && csrfToken?has_content>
        <input type="hidden" name="${csrfField}" value="${csrfToken}"/>
</#if>
        <button type="submit">修改并登录</button>
    </form>
    <p class="signin-hint">至少 5 个字符，不能和当前密码相同；改完直接进系统，以后用新密码登录。</p>
</main>
<p class="signin-foot">© 2026 天津朗华博基科技发展有限公司</p>
</body>
</html>
