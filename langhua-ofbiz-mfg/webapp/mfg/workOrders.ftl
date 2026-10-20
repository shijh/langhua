<#ftl encoding="UTF-8">
<#--
  Work order list and creation form.
  Data: listResult.workOrders, productList.products, parameters.statusId
-->
<#assign lr = (listResult)!{}>
<#assign orders = (lr.workOrders)![]>
<#assign pl = (productList)!{}>
<#assign products = (pl.products)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>工单 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.4em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .ok { color: #1a7f37; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        form.inline { display: inline; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main"><b>工单</b></a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>工单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>新建工单</legend>
    <form method="post" action="/mfg/control/workOrderCreate">
        <p>
            产品型号
            <select name="productCode">
                <#list products as p>
                    <option value="${p.productCode}">${p.productCode}<#if p.productName??> ${p.productName}</#if></option>
                </#list>
            </select>
            数量 <input type="text" name="plannedQty" size="6" value="100"/>
            交期 <input type="text" name="dueDate" size="11" placeholder="yyyy-MM-dd"/>
            客户 <input type="text" name="customerName" size="14"/>
            SMT 外协 <input type="text" name="smtVendor" size="12"/>
            <input type="submit" value="创建工单"/>
        </p>
        <#if !products?has_content>
            <p class="hint">还没有产品型号，请先到 <a href="/mfg/control/products">产品与 BOM</a> 创建。</p>
        </#if>
    </form>
</fieldset>

<h2>工单列表</h2>
<#if orders?has_content>
    <table>
        <tr>
            <th>工单号</th>
            <th>产品型号</th>
            <th>客户</th>
            <th>SMT 外协</th>
            <th class="num">计划数量</th>
            <th class="num">已报工时</th>
            <th>交期</th>
            <th>状态</th>
            <th>创建时间</th>
        </tr>
        <#list orders as o>
            <tr>
                <td><a href="/mfg/control/workOrder?workOrderId=${o.workOrderId}">${o.workOrderId}</a></td>
                <td>${o.productCode!}</td>
                <td>${o.customerName!}</td>
                <td>${o.smtVendor!}</td>
                <td class="num">${(o.plannedQty)?string("0")}</td>
                <td class="num">${(o.reportedHours)?string("0.##")}</td>
                <td><#if o.dueDate??>${o.dueDate?string("yyyy-MM-dd")}</#if></td>
                <td>${o.statusId!}</td>
                <td><#if o.createdStamp??>${o.createdStamp?string("MM-dd HH:mm")}</#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有工单。</p>
</#if>
</body>
</html>
