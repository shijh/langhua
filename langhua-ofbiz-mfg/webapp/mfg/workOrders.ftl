<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Work order list and creation form.
  Data: listResult.workOrders, productList.products, parameters.statusId
-->
<#assign lr = (listResult)!{}>
<#assign orders = (lr.workOrders)![]>
<#assign pl = (productList)!{}>
<#assign products = (pl.products)![]>
<#assign errList = (errorMessageList)![]>

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
<form method="get" action="/mfg/control/main">
    搜索 <input type="text" name="search" size="24" value="${(lr.search)!''}" placeholder="工单号 / 型号 / 客户 / 外协"/>
    状态
    <select name="statusId">
        <option value="">（全部）</option>
        <option value="MFG_CREATED"<#if (parameters.statusId!'') == 'MFG_CREATED'> selected="selected"</#if>>已创建</option>
        <option value="MFG_IN_PROGRESS"<#if (parameters.statusId!'') == 'MFG_IN_PROGRESS'> selected="selected"</#if>>生产中</option>
        <option value="MFG_COMPLETED"<#if (parameters.statusId!'') == 'MFG_COMPLETED'> selected="selected"</#if>>已完工</option>
        <option value="MFG_CLOSED"<#if (parameters.statusId!'') == 'MFG_CLOSED'> selected="selected"</#if>>已关闭</option>
    </select>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(lr.totalCount)!0} 条</span>
</form>
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
<#if (lr.pageCount?default(1)) gt 1>
    <p class="pager">
        第 ${(lr.page)!1} / ${(lr.pageCount)!1} 页（共 ${(lr.totalCount)!0} 条）
        <#if (lr.page?default(1)) gt 1>
            <a href="/mfg/control/main?search=${(lr.search)!''?url}&statusId=${(parameters.statusId)!''?url}&page=1">首页</a>
            <a href="/mfg/control/main?search=${(lr.search)!''?url}&statusId=${(parameters.statusId)!''?url}&page=${(lr.page)-1}">上一页</a>
        </#if>
        <#if (lr.page?default(1)) lt (lr.pageCount?default(1))>
            <a href="/mfg/control/main?search=${(lr.search)!''?url}&statusId=${(parameters.statusId)!''?url}&page=${(lr.page)+1}">下一页</a>
            <a href="/mfg/control/main?search=${(lr.search)!''?url}&statusId=${(parameters.statusId)!''?url}&page=${(lr.pageCount)!1}">末页</a>
        </#if>
    </p>
</#if>

