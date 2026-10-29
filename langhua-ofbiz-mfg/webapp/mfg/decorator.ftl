<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Layout decorator of the MES screens: html shell, shared stylesheet and the main navigation.
  Screens only render their content into the "body" decorator section, so adding a menu entry or
  changing the styling is a single file change.

  Context: pageTitle and activeMenu are set by the screen actions.
-->
<#assign menu = (activeMenu)!''>
<#assign pwd = (pwdStatus)!{}>
<#assign mustChange = ((pwd.mustChange)!'') == 'Y'>
<#assign perm = (permMap)!{}>
<#assign canEng = ((perm.canEng)!'N') == 'Y'>
<#assign canStock = ((perm.canStock)!'N') == 'Y'>
<#assign canSales = ((perm.canSales)!'N') == 'Y'>
<#assign canProduce = ((perm.canProduce)!'N') == 'Y'>
<#assign canCost = ((perm.canCost)!'N') == 'Y'>
<#assign canManageAccounts = ((perm.manager)!'N') == 'Y'>
<#assign errList = (errorMessageList)![]>
<#macro navItem key href label>
    <#if menu == key><a href="${href}" class="active"><b>${label}</b></a><#else><a href="${href}">${label}</a></#if>
</#macro>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${(pageTitle)!'Hardware Assembly'} - Hardware Assembly</title>
    <link rel="icon" type="image/png" sizes="32x32" href="/mfg/images/logo-32.png"/>
    <link rel="icon" type="image/png" sizes="64x64" href="/mfg/images/logo-64.png"/>
    <link rel="apple-touch-icon" href="/mfg/images/logo-180.png"/>
    <link rel="stylesheet" type="text/css" href="/mfg/css/mfg.css?v=3"/>
<#--
  A login that was asked for because the session ran out renders the start page in place (the
  framework answers the login form by rendering the main view, so the address bar still says
  "login"). The start page request is what hands the person back to the page they were on, so this
  first render goes there: MfgLoginEvents.backAfterLogin redirects and forgets the address; with
  nothing remembered the page just stays.
-->
<#if ((sessionAttributes.mfgLoginReturnTo)!'')?has_content>
    <script>window.location.replace('/mfg/control/main');</script>
</#if>
</head>
<body>
<#if mustChange>
<#-- the account still uses the password of a fresh database: nothing else is reachable until it is changed -->
<p class="nav">
    <img class="brand" src="/mfg/images/logo-64.png" alt="" width="26" height="26"/>
    <b>修改密码</b>
    <a href="/mfg/control/logout" class="logout">退出登录</a>
</p>
<h1>请先修改默认密码</h1>
<p class="error">${(pwd.message)!'当前账号还在用出厂密码，请先改成自己的密码再继续使用'}</p>
<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
<form method="post" action="/mfg/control/changePasswordSave">
    <fieldset>
        <legend>账号 ${(pwd.userLoginId)!''}</legend>
        <p>
            当前密码 <input type="password" name="currentPassword" size="20" autocomplete="current-password"/>
            新密码 <input type="password" name="newPassword" size="20" autocomplete="new-password"/>
            再输一遍 <input type="password" name="newPasswordVerify" size="20" autocomplete="new-password"/>
        </p>
        <p>
            密码提示（可选）<input type="text" name="passwordHint" size="30"/>
            <input type="submit" value="修改密码"/>
        </p>
        <p class="hint">至少 5 个字符，不能和当前密码相同；改完会自动退出，请用新密码重新登录。忘记密码时看
            <code>partymgr</code> 的 Profile 页，或让管理员重置。</p>
    </fieldset>
</form>
<#else>
<p class="nav">
    <img class="brand" src="/mfg/images/logo-64.png" alt="" width="26" height="26"/>
    <@navItem 'main' '/mfg/control/main' '工单'/>
    <#if canEng>
        <@navItem 'products' '/mfg/control/products' '产品与 BOM'/>
        <@navItem 'dev' '/mfg/control/dev' '产品开发'/>
        <@navItem 'software' '/mfg/control/software' '软件项目'/>
        <@navItem 'eco' '/mfg/control/eco' '设计变更'/>
    </#if>
    <#if canStock>
        <@navItem 'materials' '/mfg/control/materials' '物料批次'/>
        <@navItem 'receipts' '/mfg/control/receipts' '采购到货'/>
        <@navItem 'stock' '/mfg/control/stock' '库存'/>
        <@navItem 'locations' '/mfg/control/locations' '库位'/>
    </#if>
    <#if canProduce><@navItem 'shortage' '/mfg/control/shortage' '齐套预警'/></#if>
    <#if canSales>
        <@navItem 'salesOrders' '/mfg/control/salesOrders' '销售订单'/>
        <@navItem 'delivery' '/mfg/control/delivery' '出货单'/>
    </#if>
    <#if canProduce><@navItem 'vendors' '/mfg/control/vendors' '外协厂家'/></#if>
    <#if canSales>
        <@navItem 'claims' '/mfg/control/claims' '售后单'/>
        <@navItem 'buyers' '/mfg/control/buyers' '买家名单'/>
    </#if>
    <#if canProduce>
        <@navItem 'dashboard' '/mfg/control/dashboard' '看板'/>
        <@navItem 'reports' '/mfg/control/reports' '报表'/>
        <@navItem 'sn' '/mfg/control/sn' 'SN 查询'/>
    </#if>
    <#if canCost><@navItem 'quality' '/mfg/control/quality' '质量指标'/></#if>
    <#if canManageAccounts><@navItem 'users' '/mfg/control/users' '用户账号'/>
<@navItem 'settings' '/mfg/control/settings' '系统设置'/></#if>
    <a href="/mfg/control/logout" class="logout">退出登录</a>
</p>

<#if sections??>${sections.render("body")}</#if>
</#if>
<script src="/mfg/js/session.js"></script>
<script src="/mfg/js/jump.js"></script>
<script src="/mfg/js/once.js"></script>
<script src="/mfg/js/expand.js"></script>
</body>
</html>
