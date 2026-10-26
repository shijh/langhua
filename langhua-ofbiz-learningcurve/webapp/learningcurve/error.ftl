<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Error page of the learning curve screens (Step 123).

  controller.xml points <errorpage> here, so every request the framework could not finish lands on
  this page instead of the stock "ERROR MESSAGE" one. The page is deliberately quiet: what happened
  in one sentence, the technical detail in a scrollable block (the ControlServlet already HTML
  encoded it, so it is printed as it is), and two ways out.

  Context: request (a real HttpServletRequest), session, userLogin.
-->
<#assign detail = (request.getAttribute('_ERROR_MESSAGE_'))!''>
<#assign uri = (request.getRequestURI())!''>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>出错了 - 花石箓 · 学习曲线</title>
    <link rel="icon" type="image/png" sizes="32x32" href="/learningcurve/images/logo-32.png"/>
    <link rel="icon" type="image/png" sizes="64x64" href="/learningcurve/images/logo-64.png"/>
    <link rel="apple-touch-icon" href="/learningcurve/images/logo-180.png"/>
    <link rel="stylesheet" type="text/css" href="/learningcurve/css/lc.css?v=3"/>
</head>
<body class="signin">
<main class="signin-card signin-card-wide">
    <img class="signin-logo" src="/learningcurve/images/logo-180.png" alt="" width="72" height="72"/>
    <h1>这次请求没做完</h1>
    <p class="signin-sub">要么是数据不允许这么做（数量或样本不够、单号重复、没权限），要么是页面里有东西坏了。
        先回上一页看看这件事是不是已经做成了，再重试一次；同样的操作连着失败，就把下面这一段发给管理员。</p>
<#if detail?has_content>
    <pre class="signin-detail">${detail}</pre>
</#if>
    <p class="signin-hint">地址 <code>${uri?html}</code> · 日志里有完整的堆栈（<code>runtime/logs/ofbiz.log</code>）</p>
    <p class="signin-actions">
        <a class="button" href="/learningcurve/control/main">回首页</a>
        <a class="button secondary" href="javascript:history.back()">回上一页</a>
    </p>
</main>
<p class="signin-foot">© 2026 天津朗华博基科技发展有限公司</p>
</body>
</html>
