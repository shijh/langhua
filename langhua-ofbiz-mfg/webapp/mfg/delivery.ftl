<#ftl encoding="UTF-8">
<#--
  Delivery orders: shipping units to customers, which also starts the warranty.
  Data: deliveryList.deliveries, deliveryDetail.delivery, deliveryDetail.units
-->
<#assign dl = (deliveryList)!{}>
<#assign deliveries = (dl.deliveries)![]>
<#assign det = (deliveryDetail)!{}>
<#assign delivery = (det.delivery)!{}>
<#assign units = (det.units)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>出货单 - Hardware Assembly</title>
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
        textarea { font-family: Consolas, monospace; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/delivery"><b>出货单</b></a>
    <a href="/mfg/control/vendors">外协厂家</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>出货单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>新建出货单</legend>
    <form method="post" action="/mfg/control/deliverySave">
        <p>
            客户 <input type="text" name="customerName" size="18" required="required"/>
            客户订单号 <input type="text" name="customerPo" size="12" placeholder="可选"/>
            出货日期 <input type="text" name="deliveryDate" size="10" placeholder="yyyy-MM-dd"/>
            快递 <input type="text" name="carrier" size="8" value="顺丰"/>
            单号 <input type="text" name="trackingNo" size="16"/>
        </p>
        <p>
            或按工单自动选出货：工单 <input type="text" name="workOrderId" size="16" placeholder="WO-yyMMdd-NNN"/>
            数量 <input type="text" name="qty" size="5" placeholder="如 10"/>
        </p>
        <p>
            SN 列表（可选，每行或用逗号分隔；不填则按上面的工单 + 数量自动选取未出货的 SN）
        </p>
        <textarea name="sns" rows="4" cols="80" placeholder="ITMFG-261021-0001&#10;ITMFG-261021-0002"></textarea>
        <p><input type="submit" value="创建出货单"/></p>
    </form>
    <p class="hint">出货后 SN 状态变为 MFG_SHIPPED，出货日期即保修起算日。</p>
</fieldset>

<h2>出货记录</h2>
<form method="get" action="/mfg/control/delivery">
    客户：<input type="text" name="customerName" size="18" value="${(parameters.customerName)!''}"/>
    <input type="submit" value="筛选"/>
</form>

<#if deliveries?has_content>
    <table>
        <tr><th>出货单号</th><th>日期</th><th>客户</th><th>客户订单号</th>
            <th class="num">数量</th><th>快递</th><th>单号</th><th>明细</th></tr>
        <#list deliveries as d>
            <tr>
                <td>${d.deliveryId!}</td>
                <td><#if d.deliveryDate??>${d.deliveryDate?string("yyyy-MM-dd")}</#if></td>
                <td>${d.customerName!}</td>
                <td>${d.customerPo!}</td>
                <td class="num">${(d.snCount)?string("0")}</td>
                <td>${d.carrier!}</td>
                <td>${d.trackingNo!}</td>
                <td><a href="/mfg/control/delivery?deliveryId=${d.deliveryId}">查看</a></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有出货记录。</p>
</#if>

<#if (delivery.deliveryId)??>
    <h2>出货明细 ${delivery.deliveryId}（${delivery.customerName!}）</h2>
    <p class="hint">
        出货日期 <#if delivery.deliveryDate??><b>${delivery.deliveryDate?string("yyyy-MM-dd")}</b>（保修起算）</#if>
        <#if delivery.workOrderId??> / 来源工单 ${delivery.workOrderId}</#if>
        <#if delivery.trackingNo??> / 快递 ${delivery.carrier!} ${delivery.trackingNo}</#if>
    </p>
    <table>
        <tr><th>SN</th><th>产品型号</th><th>工单</th><th>状态</th></tr>
        <#list units as u>
            <tr>
                <td><a href="/mfg/control/sn?sn=${u.sn}">${u.sn!}</a></td>
                <td>${u.productCode!}</td>
                <td>${u.workOrderId!}</td>
                <td>${u.statusId!}</td>
            </tr>
        </#list>
    </table>
</#if>
</body>
</html>
