<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Sign-in page of the hardware assembly screens (Step 123).

  The framework renders the "login" view whenever a page needs a login - which happens every time
  a session runs out - so this is the page the people on the shop floor see most often. It replaces
  the stock OFBiz screen (a white table with an English title and the Apache footer) with the same
  dark card as the rest of the component.

  The form posts to the framework's own login request with the field names it expects
  (USERNAME / PASSWORD), so nothing about authentication changes; a wrong password comes back here
  with errorMessageList filled in, and the name that was typed is kept.

  Context: loginUser (what was typed last time), csrfName / csrfValue (empty unless the instance
  turns the CSRF defence on), errorMessageList (set by the screen renderer on a failed login).
-->
<#assign errList = ((loginErrors)!((errorMessageList)![]))>
<#assign typed = (loginUser)!''>
<#assign csrfField = (csrfName)!''>
<#assign csrfToken = (csrfValue)!''>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>登录 - 花石箓 · 硬件组装 MES</title>
    <link rel="icon" type="image/png" sizes="32x32" href="/mfg/images/logo-32.png"/>
    <link rel="icon" type="image/png" sizes="64x64" href="/mfg/images/logo-64.png"/>
    <link rel="apple-touch-icon" href="/mfg/images/logo-180.png"/>
    <link rel="stylesheet" type="text/css" href="/mfg/css/mfg.css?v=3"/>
</head>
<body class="signin">
<main class="signin-card">
    <img class="signin-logo" src="/mfg/images/logo-180.png" alt="" width="72" height="72"/>
    <h1>花石箓 · 硬件组装 MES</h1>
    <p class="signin-sub">产品与 BOM · 工单 · 报工 · 库存 · 成本</p>
<#if errList?has_content>
    <p class="signin-error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
    <form method="post" action="/mfg/control/login">
        <label class="signin-field">账号
            <input type="text" name="USERNAME" value="${typed}" autocomplete="username"
                   autocapitalize="off" spellcheck="false"<#if !typed?has_content> autofocus</#if> required/>
        </label>
        <label class="signin-field">密码
            <input type="password" name="PASSWORD" autocomplete="current-password"<#if typed?has_content> autofocus</#if> required/>
        </label>
<#if csrfField?has_content && csrfToken?has_content>
        <input type="hidden" name="${csrfField}" value="${csrfToken}"/>
</#if>
        <button type="submit">登录</button>
    </form>
    <p class="signin-hint">忘记密码请让管理员在「用户账号」页把密码重置回出厂值，再用新密码登录。</p>
</main>
<p class="signin-foot">© 2026 天津朗华博基科技发展有限公司</p>
</body>
</html>
