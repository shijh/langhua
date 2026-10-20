<#ftl encoding="UTF-8">
<#--
  Warranty claims (after sales). Warranty status is derived from the delivery date and the product
  warranty months, so the operator immediately sees whether the repair is covered.
  Data: claimList.claims, claimList.claimCount, claimList.openCount, claimDetail.*
-->
<#assign cl = (claimList)!{}>
<#assign claims = (cl.claims)![]>
<#assign det = (claimDetail)!{}>
<#assign claim = (det.claim)!{}>
<#assign snRecord = (det.snRecord)!{}>
<#assign deliveryInfo = (det.delivery)!{}>
<#assign replacements = (det.replacements)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>售后单 - Hardware Assembly</title>
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
        .warn { color: #9a6700; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
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
    <a href="/mfg/control/claims"><b>售后单</b></a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>售后单（保修）</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p>
    共 ${(cl.claimCount)!0} 单，其中未结 <span class="big warn">${(cl.openCount)!0}</span> 单。
    <span class="hint">保修到期日 = 出货日 + 产品型号的保修月数（默认 12 个月）。</span>
</p>

<fieldset>
    <legend>登记售后单</legend>
    <form method="post" action="/mfg/control/claimCreate">
        <p>
            SN <input type="text" name="sn" size="18" required="required" placeholder="ITMFG-261021-0001"/>
            收件日期 <input type="text" name="receivedDate" size="10" placeholder="yyyy-MM-dd"/>
            责任预判
            <select name="responsibility">
                <option value="">（待定）</option>
                <option value="HOUSE">自制</option>
                <option value="SMT">SMT 外协</option>
                <option value="SUPPLIER">来料</option>
                <option value="DESIGN">设计</option>
            </select>
        </p>
        <p>
            问题描述 <input type="text" name="issueDesc" size="70" required="required" placeholder="客户反馈：无法开机 / 蓝牙断连…"/>
        </p>
        <p>
            备注 <input type="text" name="note" size="40" placeholder="可选"/>
            <input type="submit" value="登记"/>
        </p>
    </form>
    <p class="hint">登记的 SN 必须是已存在的主板；客户与出货日会自动从出货单带出。</p>
</fieldset>

<h2>售后单列表</h2>
<form method="get" action="/mfg/control/claims">
     SN：<input type="text" name="sn" size="18" value="${(parameters.sn)!''}"/>
     状态：
    <select name="statusId">
        <option value="">（全部）</option>
        <option value="MFG_RMA_OPEN"<#if (parameters.statusId!'') == 'MFG_RMA_OPEN'> selected="selected"</#if>>未结</option>
        <option value="MFG_RMA_REPAIRED"<#if (parameters.statusId!'') == 'MFG_RMA_REPAIRED'> selected="selected"</#if>>已修好</option>
        <option value="MFG_RMA_REPLACED"<#if (parameters.statusId!'') == 'MFG_RMA_REPLACED'> selected="selected"</#if>>已换机</option>
        <option value="MFG_RMA_CLOSED"<#if (parameters.statusId!'') == 'MFG_RMA_CLOSED'> selected="selected"</#if>>已结案</option>
    </select>
    <input type="submit" value="筛选"/>
</form>

<#if claims?has_content>
    <table>
        <tr><th>单号</th><th>收件日</th><th>SN</th><th>客户</th><th>出货日</th>
            <th>保修到期</th><th>保内</th><th>问题</th><th>状态</th><th>责任</th><th class="num">工时</th></tr>
        <#list claims as c>
            <tr>
                <td><a href="/mfg/control/claims?claimId=${c.claimId}">${c.claimId!}</a></td>
                <td><#if c.receivedDate??>${c.receivedDate?string("yyyy-MM-dd")}</#if></td>
                <td><a href="/mfg/control/sn?sn=${c.sn}">${c.sn!}</a></td>
                <td>${c.customerName!}</td>
                <td><#if c.deliveryDate??>${c.deliveryDate?string("yyyy-MM-dd")}<#else>-</#if></td>
                <td><#if c.warrantyEndDate??>${c.warrantyEndDate?string("yyyy-MM-dd")}<#else>-</#if></td>
                <td><#if (c.inWarranty!'N') == 'Y'><span class="ok">保内</span><#else><span class="error">保外</span></#if></td>
                <td>${c.issueDesc!}</td>
                <td>
                    <#if (c.statusId!'') == 'MFG_RMA_CLOSED'>已结案
                    <#elseif (c.statusId!'') == 'MFG_RMA_REPAIRED'>已修好
                    <#elseif (c.statusId!'') == 'MFG_RMA_REPLACED'>已换机
                    <#else><span class="warn">未结</span></#if>
                </td>
                <td>${c.responsibility!}</td>
                <td class="num"><#if c.repairHours??>${c.repairHours?string("0.##")}</#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">没有售后单。</p>
</#if>

<#if (claim.claimId)??>
    <h2>售后单详情 ${claim.claimId}</h2>
    <table>
        <tr><th>SN</th>
            <td><a href="/mfg/control/sn?sn=${claim.sn}">${claim.sn!}</a>
                <#if (snRecord.statusId)??>（${snRecord.statusId}）</#if></td></tr>
        <tr><th>工单</th><td><a href="/mfg/control/workOrder?workOrderId=${claim.workOrderId}">${claim.workOrderId!}</a></td></tr>
        <tr><th>客户</th><td>${claim.customerName!}
            <#if (claim.deliveryId)??>（出货单 <a href="/mfg/control/delivery?deliveryId=${claim.deliveryId}">${claim.deliveryId}</a>）</#if></td></tr>
        <tr><th>出货日 / 保修到期</th>
            <td><#if claim.deliveryDate??>${claim.deliveryDate?string("yyyy-MM-dd")}<#else>-</#if>
                / <#if claim.warrantyEndDate??>${claim.warrantyEndDate?string("yyyy-MM-dd")}<#else>-</#if>
                <#if (claim.inWarranty!'N') == 'Y'><span class="ok">保内</span><#else><span class="error">保外</span></#if></td></tr>
        <tr><th>问题</th><td>${claim.issueDesc!}</td></tr>
        <tr><th>该工单不良记录数</th><td>${(det.defectCount)!0}</td></tr>
    </table>

    <h3 style="font-size:1em;margin-top:1.2em;">该板换件记录</h3>
    <#if replacements?has_content>
        <table>
            <tr><th>日期</th><th>料号</th><th>原批次</th><th>新批次</th><th class="num">数量</th><th>原因</th></tr>
            <#list replacements as r>
                <tr>
                    <td><#if r.replacedDate??>${r.replacedDate?string("yyyy-MM-dd")}</#if></td>
                    <td>${r.componentCode!}</td>
                    <td>${r.oldLotId!}</td>
                    <td>${r.newLotId!}</td>
                    <td class="num">${((r.qty)!0)?string("0.##")}</td>
                    <td>${r.reason!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">没有换件记录。</p>
    </#if>

    <fieldset>
        <legend>处理售后单</legend>
        <form method="post" action="/mfg/control/claimUpdate">
            <input type="hidden" name="claimId" value="${claim.claimId}"/>
            <p>
                状态
                <select name="statusId">
                    <option value="MFG_RMA_OPEN"<#if (claim.statusId!'') == 'MFG_RMA_OPEN'> selected="selected"</#if>>未结</option>
                    <option value="MFG_RMA_REPAIRED"<#if (claim.statusId!'') == 'MFG_RMA_REPAIRED'> selected="selected"</#if>>已修好</option>
                    <option value="MFG_RMA_REPLACED"<#if (claim.statusId!'') == 'MFG_RMA_REPLACED'> selected="selected"</#if>>已换机</option>
                    <option value="MFG_RMA_CLOSED"<#if (claim.statusId!'') == 'MFG_RMA_CLOSED'> selected="selected"</#if>>已结案</option>
                </select>
                责任
                <select name="responsibility">
                    <option value="HOUSE"<#if (claim.responsibility!'') == 'HOUSE'> selected="selected"</#if>>自制</option>
                    <option value="SMT"<#if (claim.responsibility!'') == 'SMT'> selected="selected"</#if>>SMT 外协</option>
                    <option value="SUPPLIER"<#if (claim.responsibility!'') == 'SUPPLIER'> selected="selected"</#if>>来料</option>
                    <option value="DESIGN"<#if (claim.responsibility!'') == 'DESIGN'> selected="selected"</#if>>设计</option>
                </select>
                返修工时 <input type="text" name="repairHours" size="5" value="${(claim.repairHours)!0}"/>
                备注 <input type="text" name="note" size="28" value="${claim.note!}"/>
                <input type="submit" value="保存"/>
            </p>
        </form>
        <p class="hint">需要记录换件时，到 <a href="/mfg/control/sn?sn=${claim.sn}">SN 履历</a> 页登记（会精确到单板）。</p>
    </fieldset>
</#if>
</body>
</html>
