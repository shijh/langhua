<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Layout decorator of the learning curve screens: html shell, shared stylesheet and the main
  navigation. Screens only render their content into the "body" decorator section, so adding a
  menu entry or changing the styling is a single file change.

  Context: pageTitle and activeMenu are set by the screen actions.
-->
<#assign menu = (activeMenu)!''>
<#assign pwd = (pwdStatus)!{}>
<#assign mustChange = ((pwd.mustChange)!'') == 'Y'>
<#assign errList = (errorMessageList)![]>
<#macro navItem key href label>
    <#if menu == key><a href="${href}" class="active"><b>${label}</b></a><#else><a href="${href}">${label}</a></#if>
</#macro>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${(pageTitle)!'Learning Curve'} - Learning Curve</title>
    <link rel="icon" type="image/png" sizes="32x32" href="/learningcurve/images/logo-32.png"/>
    <link rel="icon" type="image/png" sizes="64x64" href="/learningcurve/images/logo-64.png"/>
    <link rel="apple-touch-icon" href="/learningcurve/images/logo-180.png"/>
    <link rel="stylesheet" type="text/css" href="/learningcurve/css/lc.css"/>
</head>
<body>
<#if mustChange>
<p class="nav">
    <img class="brand" src="/learningcurve/images/logo-64.png" alt="" width="26" height="26"/>
    <b>修改密码</b>
</p>
<h1>请先修改默认密码</h1>
<p class="error">${(pwd.message)!'当前账号还在用出厂密码，请先改成自己的密码再继续使用'}</p>
<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
<form method="post" action="/learningcurve/control/changePasswordSave">
    <fieldset>
        <legend>账号 ${(pwd.userLoginId)!''}</legend>
        <p>
            当前密码 <input type="password" name="currentPassword" size="20" autocomplete="current-password"/>
            新密码 <input type="password" name="newPassword" size="20" autocomplete="new-password"/>
            再输一遍 <input type="password" name="newPasswordVerify" size="20" autocomplete="new-password"/>
        </p>
        <p>
            密码提示（可选）<input type="text" name="passwordHint" size="30"/>
            <input type="submit" class="secondary" value="修改密码"/>
        </p>
        <p class="hint">至少 5 个字符，不能和当前密码相同；改完自动回到估算页。</p>
    </fieldset>
</form>
<#else>
<p class="nav">
    <img class="brand" src="/learningcurve/images/logo-64.png" alt="" width="26" height="26"/>
    <@navItem 'estimate' '/learningcurve/control/main' '新建估算'/>
    <@navItem 'records' '/learningcurve/control/records' '估算记录'/>
    <@navItem 'quote' '/learningcurve/control/quote' '软件报价'/>
</p>

<#if sections??>${sections.render("body")}</#if>
</#if>
</body>
</html>
