<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Layout decorator of the learning curve screens: html shell, shared stylesheet and the main
  navigation. Screens only render their content into the "body" decorator section, so adding a
  menu entry or changing the styling is a single file change.

  Context: pageTitle and activeMenu are set by the screen actions.
-->
<#assign menu = (activeMenu)!''>
<#macro navItem key href label>
    <#if menu == key><a href="${href}" class="active"><b>${label}</b></a><#else><a href="${href}">${label}</a></#if>
</#macro>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>${(pageTitle)!'Learning Curve'} - Learning Curve</title>
    <link rel="stylesheet" type="text/css" href="/learningcurve/css/lc.css"/>
</head>
<body>
<p class="nav">
    <@navItem 'estimate' '/learningcurve/control/main' '新建估算'/>
    <@navItem 'records' '/learningcurve/control/records' '估算记录'/>
</p>

<#if sections??>${sections.render("body")}</#if>
</body>
</html>
