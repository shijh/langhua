<#ftl encoding="UTF-8">
<#--
  Serial number history.
  Data: snInfo.snRecord, snInfo.workOrder, snInfo.reports, snInfo.defects, snInfo.lots
-->
<#assign info = (snInfo)!{}>
<#assign unit = (info.snRecord)!{}>
<#assign wo = (info.workOrder)!{}>
<#assign reports = (info.reports)![]>
<#assign defects = (info.defects)![]>
<#assign lots = (info.lots)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>SN 履历 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .hint { color: #666; font-size: .85em; }
        .error { color: #b00000; font-weight: bold; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn"><b>SN 查询</b></a>
</p>

<h1>SN 履历查询</h1>

<form method="get" action="/mfg/control/sn">
    SN <input type="text" name="sn" size="20" value="${(parameters.sn)!''}"/>
    <input type="submit" value="查询"/>
</form>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<#if unit.sn??>
    <h2>${unit.sn}</h2>
    <table>
        <tr><th>型号</th><td>${unit.productCode!}</td></tr>
        <tr><th>状态</th><td>${unit.statusId!}</td></tr>
        <tr><th>工单</th><td><a href="/mfg/control/workOrder?workOrderId=${unit.workOrderId}">${unit.workOrderId}</a></td></tr>
        <#if wo.customerName??><tr><th>客户</th><td>${wo.customerName}</td></tr></#if>
        <#if wo.smtVendor??><tr><th>SMT 外协</th><td>${wo.smtVendor}</td></tr></#if>
        <#if unit.createdStamp??><tr><th>登记时间</th><td>${unit.createdStamp?string("yyyy-MM-dd HH:mm")}</td></tr></#if>
    </table>

    <h2>工序履历</h2>
    <#if reports?has_content>
        <table>
            <tr><th>时间</th><th class="num">工序</th><th>作业员</th><th class="num">工时(h)</th>
                <th class="num">良品</th><th class="num">不良</th><th>备注</th></tr>
            <#list reports as r>
                <tr>
                    <td><#if r.reportDate??>${r.reportDate?string("MM-dd HH:mm")}</#if></td>
                    <td class="num">${(r.opSeq)?string("0")}</td>
                    <td>${r.operatorName!}</td>
                    <td class="num"><#if r.workHours??>${r.workHours?string("0.##")}</#if></td>
                    <td class="num">${(r.qtyOk)?string("0")}</td>
                    <td class="num">${(r.qtyNg)?string("0")}</td>
                    <td>${r.note!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">没有报工记录。</p>
    </#if>

    <h2>不良与返修</h2>
    <#if defects?has_content>
        <table>
            <tr><th>编号</th><th class="num">工序</th><th>不良代码</th><th class="num">数量</th>
                <th>责任</th><th class="num">返修工时</th></tr>
            <#list defects as f>
                <tr>
                    <td>${f.defectId!}</td>
                    <td class="num">${(f.opSeq)?string("0")}</td>
                    <td>${f.defectCode!}</td>
                    <td class="num">${(f.qty)?string("0")}</td>
                    <td>${f.responsibility!}</td>
                    <td class="num"><#if f.repairHours??>${f.repairHours?string("0.##")}</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">没有不良记录。</p>
    </#if>

    <h2>物料追溯（批次级）</h2>
    <#if lots?has_content>
        <table>
            <tr><th>元件料号</th><th>内部批次</th><th>供应商</th><th>供应商批号</th>
                <th class="num">已发数量</th><th>位号</th><th>SN 区间</th></tr>
            <#list lots as l>
                <tr>
                    <td>${l.componentCode!}</td>
                    <td><a href="/mfg/control/materials?lotId=${l.lotId}">${l.lotId!}</a></td>
                    <td>${l.supplierName!}</td>
                    <td>${l.supplierLot!}</td>
                    <td class="num">${((l.qty)!0)?string("0.##")}</td>
                    <td>${l.designators!}</td>
                    <td><#if l.snFrom??>${l.snFrom}</#if><#if l.snTo??> ~ ${l.snTo}</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">该工单还没有发料记录（在工单页登记发料后即可追溯）。</p>
    </#if>
<#elseif (parameters.sn)?? && parameters.sn?has_content>
    <p class="error">未找到 SN：${parameters.sn}</p>
<#else>
    <p class="hint">输入 SN 查询单机的工单、工序履历、不良与物料批次。</p>
</#if>
</body>
</html>
