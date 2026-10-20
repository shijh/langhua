<#ftl encoding="UTF-8">
<#--
  Shop floor dashboard.
  Data: board.openOrderCount, board.openPlannedQty, board.todayHours,
        board.todayQtyOk, board.todayQtyNg, board.todayYield, board.recentReports
-->
<#assign b = (board)!{}>
<#assign reports = (b.recentReports)![]>
<#assign yieldRate = (b.todayYield)!0>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>看板 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        .nav a { margin-right: 1em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .cards { display: flex; gap: 1em; margin-top: 1em; flex-wrap: wrap; }
        .card { border: 1px solid #ddd; border-radius: 6px; padding: .8em 1.2em; min-width: 9em; }
        .card .label { color: #666; font-size: .85em; }
        .card .value { font-size: 1.6em; font-weight: bold; margin-top: .2em; }
        .hint { color: #666; font-size: .85em; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/dashboard"><b>看板</b></a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>车间看板</h1>

<div class="cards">
    <div class="card">
        <div class="label">在制工单</div>
        <div class="value">${(b.openOrderCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">在制计划台数</div>
        <div class="value">${(b.openPlannedQty)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">今日工时 (h)</div>
        <div class="value">${(b.todayHours)?string("0.##")}</div>
    </div>
    <div class="card">
        <div class="label">今日良品</div>
        <div class="value">${(b.todayQtyOk)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">今日不良</div>
        <div class="value">${(b.todayQtyNg)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">今日良率</div>
        <div class="value"><#if (b.todayQtyOk)?default(0) + (b.todayQtyNg)?default(0) gt 0>${(yieldRate * 100)?string("0.0")}%<#else>-</#if></div>
    </div>
</div>

<h2>最近报工</h2>
<#if reports?has_content>
    <table>
        <tr><th>时间</th><th>工单</th><th class="num">工序</th><th>作业员</th>
            <th class="num">工时(h)</th><th class="num">良品</th><th class="num">不良</th><th>SN</th></tr>
        <#list reports as r>
            <tr>
                <td><#if r.reportDate??>${r.reportDate?string("MM-dd HH:mm")}</#if></td>
                <td><a href="/mfg/control/workOrder?workOrderId=${r.workOrderId}">${r.workOrderId}</a></td>
                <td class="num">${(r.opSeq)?string("0")}</td>
                <td>${r.operatorName!}</td>
                <td class="num"><#if r.workHours??>${r.workHours?string("0.##")}</#if></td>
                <td class="num">${(r.qtyOk)?string("0")}</td>
                <td class="num">${(r.qtyNg)?string("0")}</td>
                <td><#if r.sn??><a href="/mfg/control/sn?sn=${r.sn}">${r.sn}</a></#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有报工数据。</p>
</#if>
</body>
</html>
