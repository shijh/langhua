<#--
  List of stored estimates (newest first, optional productCode filter).
  Data: listResult.estimates (list of maps)
-->
<#assign lr = (listResult)!{}>
<#assign estimates = (lr.estimates)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>Learning Curve Records</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 2em; color: #222; }
        h1 { font-size: 1.5em; }
        table { border-collapse: collapse; margin-top: 1em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .empty { color: #666; margin-top: 1em; }
    </style>
</head>
<body>
<h1>估算记录</h1>

<p>
    <a href="/learningcurve/control/main">← 新建估算</a>
</p>

<form method="get" action="/learningcurve/control/records">
    产品型号 / 项目：<input type="text" name="productCode" size="24" value="${(parameters.productCode)!''}"/>
    <input type="submit" value="筛选"/>
</form>

<#if estimates?has_content>
    <table>
        <tr>
            <th>时间</th>
            <th>产品型号</th>
            <th>口径</th>
            <th>方法</th>
            <th class="num">待估算单位</th>
            <th class="num">总额</th>
            <th class="num">单位值</th>
            <th class="num">R&sup2;</th>
            <th>操作</th>
        </tr>
        <#list estimates as e>
            <tr>
                <td><#if e.createdStamp??>${e.createdStamp?string("yyyy-MM-dd HH:mm")}</#if></td>
                <td>${e.productCode!}</td>
                <td>${e.measureType!}</td>
                <td><#if (e.methodType?int) == 2>Crawford<#else>Wright</#if></td>
                <td class="num">${(e.newUnitNumber)?string("0.####")}</td>
                <td class="num">${(e.estimateTotal)?string("0.####")}</td>
                <td class="num">${(e.estimateUnit)?string("0.####")}</td>
                <td class="num">${(e.leastSquareActual)?string("0.####")}</td>
                <td>
                    <a href="/learningcurve/control/recordDetail?estimateId=${e.estimateId}">详情</a>
                    |
                    <a href="/learningcurve/control/exportCsv?estimateId=${e.estimateId}">CSV</a>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="empty">还没有保存的估算记录。</p>
</#if>
</body>
</html>
