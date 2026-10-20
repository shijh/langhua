<#ftl encoding="UTF-8">
<#--
  Material lot ledger and backward trace.
  Data: lotResult.lots, trace.lots, trace.workOrders, trace.totalSnCount
-->
<#assign lr = (lotResult)!{}>
<#assign lots = (lr.lots)![]>
<#assign tr = (trace)!{}>
<#assign tracedLots = (tr.lots)![]>
<#assign tracedOrders = (tr.workOrders)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>物料批次 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .ok { color: #1a7f37; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/delivery">出货单</a>
    <a href="/mfg/control/vendors">外协厂家</a>
    <a href="/mfg/control/claims">售后单</a>
    <a href="/mfg/control/buyers">买家名单</a>
    <a href="/mfg/control/quality">质量指标</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>物料批次</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>登记批次（采购到货）</legend>
    <form method="post" action="/mfg/control/lotSave">
        <p>
            元件料号 <input type="text" name="componentCode" size="16" placeholder="如 U1-BT5-SOC"/>
            名称 <input type="text" name="componentName" size="18" placeholder="可选"/>
            供应商 <input type="text" name="supplierName" size="12" placeholder="可选"/>
            供应商批号 <input type="text" name="supplierLot" size="14" placeholder="DateCode / 料盘号"/>
            数量 <input type="text" name="receivedQty" size="8" value="1"/>
            到货日期 <input type="text" name="receivedDate" size="10" placeholder="yyyy-MM-dd"/>
            <input type="submit" value="保存批次"/>
        </p>
    </form>
    <p class="hint">批次号自动生成（LOT-yyMMdd-NN）；同一料号可登记多个批次。</p>
</fieldset>

<fieldset>
    <legend>批量导入到货（CSV）</legend>
    <form method="post" action="/mfg/control/lotImportCsv">
        <textarea name="csv" rows="6" cols="90">U1-BT5-SOC,主控 SoC,立创商城,DC2410A,120,2026-10-20
C0402-100N,100nF 0402 电容,立创商城,DC2409B,500,2026-10-19</textarea>
        <p class="hint">每行：料号,名称,供应商,供应商批号/DateCode,数量,到货日期(yyyy-MM-dd，可空)</p>
        <p><input type="submit" value="导入"/></p>
    </form>
    <#if (parameters.importedCount)??>
        <p class="ok">已导入 ${parameters.importedCount} 个批次。</p>
    </#if>
</fieldset>

<h2>批次台账</h2>
<form method="get" action="/mfg/control/materials">
    元件料号：<input type="text" name="componentCode" size="18" value="${(parameters.componentCode)!''}"/>
    <input type="submit" value="筛选"/>
</form>

<#if lots?has_content>
    <table>
        <tr><th>批次</th><th>元件料号</th><th>名称</th><th>供应商</th><th>供应商批号</th>
            <th class="num">到货</th><th class="num">已发</th><th class="num">剩余</th><th>到货日期</th><th>追溯</th></tr>
        <#list lots as lot>
            <tr>
                <td>${lot.lotId!}</td>
                <td>${lot.componentCode!}</td>
                <td>${lot.componentName!}</td>
                <td>${lot.supplierName!}</td>
                <td>${lot.supplierLot!}</td>
                <td class="num">${((lot.receivedQty)!0)?string("0.##")}</td>
                <td class="num">${((lot.issuedQty)!0)?string("0.##")}</td>
                <td class="num">${((lot.remainingQty)!0)?string("0.##")}</td>
                <td><#if lot.receivedDate??>${lot.receivedDate?string("yyyy-MM-dd")}</#if></td>
                <td><a href="/mfg/control/materials?lotId=${lot.lotId}">查影响范围</a></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有批次记录。</p>
</#if>

<h2>批次追溯（反向：批次 → 工单 → SN）</h2>
<form method="get" action="/mfg/control/materials">
    内部批次：<input type="text" name="lotId" size="16" value="${(parameters.lotId)!''}" placeholder="LOT-261020-01"/>
    或 供应商批号：<input type="text" name="supplierLot" size="16" value="${(parameters.supplierLot)!''}"/>
    <input type="submit" value="追溯"/>
</form>

<#if tracedLots?has_content>
    <p>
        命中批次 ${tracedLots?size} 个，涉及工单 ${tracedOrders?size} 张，
        累计已生产 SN <span class="big">${(tr.totalSnCount)!0}</span> 台。
    </p>
    <#if tracedOrders?has_content>
        <table>
            <tr><th>工单</th><th>产品型号</th><th>状态</th><th>客户/项目</th>
                <th class="num">计划数量</th><th class="num">已建 SN</th><th class="num">不良记录</th>
                <th>推定 SN 区间（FIFO）</th></tr>
            <#list tracedOrders as order>
                <tr>
                    <td><a href="/mfg/control/workOrder?workOrderId=${order.workOrderId}">${order.workOrderId!}</a></td>
                    <td>${order.productCode!}</td>
                    <td>${order.statusId!}</td>
                    <td>${order.customerName!}</td>
                    <td class="num">${(order.plannedQty)?string("0")}</td>
                    <td class="num">${(order.snCount)?string("0")}</td>
                    <td class="num">${(order.defectCount)?string("0")}</td>
                    <td>
                        <#list (order.estimates)![] as est>
                            ${est.componentCode!}: ${est.estimatedSnFrom!} ~ ${est.estimatedSnTo!}
                            <span class="hint">(${est.estimatedSnCount!0}/${est.issueCount!0})</span><br/>
                        </#list>
                    </td>
                </tr>
            </#list>
        </table>
        <p class="hint">推定区间是把工单内的 SN 按发料先后均分得出的估算值（SMT 厂不提供换料记录），
            用于缩小排查范围；若需要精确到单板，请在 SN 页登记实际换件记录。</p>
        <p class="hint">这就是供应商报批次问题时的召回范围：上面所有 SN 都用了该批次。</p>
    <#else>
        <p class="hint">该批次还没有发料给任何工单（或尚未回厂建 SN）。</p>
    </#if>
<#else>
    <#if (parameters.lotId)?? || (parameters.supplierLot)??>
        <p class="hint">没有找到匹配的批次。</p>
    </#if>
</#if>
</body>
</html>
