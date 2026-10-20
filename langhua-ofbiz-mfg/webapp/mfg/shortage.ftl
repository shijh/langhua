<#ftl encoding="UTF-8">
<#--
  Material shortage warning: open work orders ordered by due date with the lines that are short.
  Data: shortage.orders, shortage.orderCount, shortage.shortageOrderCount
-->
<#assign sh = (shortage)!{}>
<#assign orders = (sh.orders)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>齐套预警 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; vertical-align: top; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .ok { color: #1a7f37; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        .big { font-size: 1.3em; font-weight: bold; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/delivery">出货单</a>
    <a href="/mfg/control/vendors">外协厂家</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>齐套预警</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p>
    在制工单 ${(sh.orderCount)!0} 张，其中 <span class="big error">${(sh.shortageOrderCount)!0}</span> 张欠料。
    <span class="hint">需求 = BOM 用量 × 计划数量；欠料 = 需求 − 已发料。</span>
</p>

<#if orders?has_content>
    <table>
        <tr>
            <th>交期</th><th>工单</th><th>产品型号</th><th class="num">计划数</th>
            <th>客户/项目</th><th>SMT 外协</th><th class="num">欠料行</th>
            <th class="num">欠料总量</th><th>欠料明细（前 5 行）</th>
        </tr>
        <#list orders as order>
            <tr>
                <td><#if order.dueDate??>${order.dueDate?string("yyyy-MM-dd")}<#else>-</#if></td>
                <td><a href="/mfg/control/workOrder?workOrderId=${order.workOrderId}">${order.workOrderId!}</a></td>
                <td>${order.productCode!}</td>
                <td class="num">${(order.plannedQty)?string("0")}</td>
                <td>${order.customerName!}</td>
                <td>${order.smtVendor!}</td>
                <td class="num">
                    <#if (order.shortageLineCount)!0 gt 0>
                        <span class="error">${order.shortageLineCount}</span>/<#if order.lineCount??>${order.lineCount}</#if>
                    <#else>
                        <span class="ok">0</span>/<#if order.lineCount??>${order.lineCount}</#if>
                    </#if>
                </td>
                <td class="num"><#if ((order.shortageQty)!0) gt 0>${order.shortageQty?string("0.##")}<#else>-</#if></td>
                <td>
                    <#list (order.shortages)![] as line>
                        <#if line_index lt 5>
                            ${line.componentCode!}<#if (line.isKeyPart!'N') == 'Y'> <b>★</b></#if>
                            欠 ${((line.requiredQty)!0 - (line.issuedQty)!0)?string("0.##")}
                            <span class="hint">(需 ${((line.requiredQty)!0)?string("0.##")} / 发 ${((line.issuedQty)!0)?string("0.##")})</span><br/>
                        </#if>
                    </#list>
                    <#if ((order.shortageLineCount)!0) gt 5><span class="hint">…还有 ${order.shortageLineCount - 5} 行</span></#if>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">没有在制工单。</p>
</#if>
</body>
</html>
