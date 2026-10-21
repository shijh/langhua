<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Layout decorator of the MES screens: html shell, shared stylesheet and the main navigation.
  Screens only render their content into the "body" decorator section, so adding a menu entry or
  changing the styling is a single file change.

  Context: pageTitle and activeMenu are set by the screen actions.
-->
<#assign menu = (activeMenu)!''>
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
    <link rel="stylesheet" type="text/css" href="/mfg/css/mfg.css"/>
</head>
<body>
<p class="nav">
    <img class="brand" src="/mfg/images/logo-64.png" alt="" width="26" height="26"/>
    <@navItem 'main' '/mfg/control/main' '工单'/>
    <@navItem 'products' '/mfg/control/products' '产品与 BOM'/>
    <@navItem 'dev' '/mfg/control/dev' '产品开发'/>
    <@navItem 'software' '/mfg/control/software' '软件项目'/>
    <@navItem 'eco' '/mfg/control/eco' '设计变更'/>
    <@navItem 'materials' '/mfg/control/materials' '物料批次'/>
    <@navItem 'shortage' '/mfg/control/shortage' '齐套预警'/>
    <@navItem 'delivery' '/mfg/control/delivery' '出货单'/>
    <@navItem 'vendors' '/mfg/control/vendors' '外协厂家'/>
    <@navItem 'claims' '/mfg/control/claims' '售后单'/>
    <@navItem 'buyers' '/mfg/control/buyers' '买家名单'/>
    <@navItem 'quality' '/mfg/control/quality' '质量指标'/>
    <@navItem 'dashboard' '/mfg/control/dashboard' '看板'/>
    <@navItem 'sn' '/mfg/control/sn' 'SN 查询'/>
</p>

<#if sections??>${sections.render("body")}</#if>
</body>
</html>
