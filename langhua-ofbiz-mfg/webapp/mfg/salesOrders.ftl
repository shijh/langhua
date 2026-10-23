<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  销售订单：列表 + 新建（多行明细），每张单显示下单 / 交期 / 已预留 / 已出货 / 还欠多少。
  数据：orderResult.orders（salesOrderId / customerName / customerPo / orderDate / dueDate / statusId /
        lineCount / qtyTotal / reservedTotal / shippedTotal / openTotal）、分页字段、statuses
-->
<#assign or = (orderResult)!{}>
<#assign orders = (or.orders)![]>
<#assign statuses = (or.statuses)![]>
<#assign errList = (errorMessageList)![]>

<#function soLabel id>
    <#if (id!'') == 'MFG_SO_OPEN'><#return '待料 / 待排产'>
    <#elseif (id!'') == 'MFG_SO_PLANNED'><#return '已排产'>
    <#elseif (id!'') == 'MFG_SO_SHIPPED'><#return '已出货'>
    <#elseif (id!'') == 'MFG_SO_CLOSED'><#return '已关闭'>
    <#elseif (id!'') == 'MFG_SO_CANCELLED'><#return '已取消'>
    <#else><#return '${id!}'>
    </#if>
</#function>

<h1>销售订单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>新建订单</legend>
    <form method="post" action="/mfg/control/salesOrderSave">
        <p>
            客户 <input type="text" name="customerName" size="18" placeholder="必填"/>
            客户订单号 <input type="text" name="customerPo" size="14"/>
            下单日 <input type="text" name="orderDate" size="10" placeholder="yyyy-mm-dd"/>
            交期 <input type="text" name="dueDate" size="10" placeholder="yyyy-mm-dd"/>
        </p>
        <p>
            <textarea name="lines" rows="4" cols="80" placeholder="型号,数量[,交期]"># 型号,数量[,交期]
</textarea>
        </p>
        <p class="hint">一行一个型号，例如 <code>BT5-PRO,20,2026-11-30</code>；保存后系统先把库里已有的成品锁给这张单，
            不够的部分到详情页点「排产」自动开工单。</p>
        <p><input type="submit" value="保存订单"/></p>
    </form>
</fieldset>

<form method="get" action="/mfg/control/salesOrders">
    搜索 <input type="text" name="search" size="18" value="${(or.search)!''}" placeholder="单号 / 客户 / 客户订单号"/>
    状态
    <select name="statusId">
        <option value="">全部</option>
        <#list statuses as status>
            <option value="${status}"<#if (or.statusId!'') == status> selected="selected"</#if>>${soLabel(status)}</option>
        </#list>
    </select>
    <input type="submit" value="查询"/>
</form>

<#if orders?has_content>
    <table id="orderTable">
        <tr>
            <th>单号</th><th>客户</th><th>客户订单号</th><th>下单</th><th>交期</th><th>状态</th>
            <th class="num">行数</th><th class="num">数量</th><th class="num">已预留</th>
            <th class="num">已出货</th><th class="num">欠货</th><th>操作</th>
        </tr>
        <#list orders as order>
            <tr<#if (order.statusId!'') == 'MFG_SO_SHIPPED'> class="okRow"</#if> data-status="${order.statusId!}">
                <td><a href="/mfg/control/salesOrder?salesOrderId=${order.salesOrderId}">${order.salesOrderId}</a></td>
                <td>${order.customerName!}</td>
                <td>${order.customerPo!}</td>
                <td><#if order.orderDate??>${order.orderDate?string("MM-dd")}</#if></td>
                <td><#if order.dueDate??>${order.dueDate?string("MM-dd")}</#if></td>
                <td><#if (order.statusId!'') == 'MFG_SO_SHIPPED'><span class="ok">${soLabel(order.statusId)}</span><#elseif (order.statusId!'') == 'MFG_SO_PLANNED'><span class="warn">${soLabel(order.statusId)}</span><#else>${soLabel(order.statusId)}</#if></td>
                <td class="num">${(order.lineCount)!0}</td>
                <td class="num">${(order.qtyTotal!0)?string("0.##")}</td>
                <td class="num"><#if (order.reservedTotal!0) gt 0><span class="warn">${order.reservedTotal?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td class="num">${(order.shippedTotal!0)?string("0.##")}</td>
                <td class="num"><#if (order.openTotal!0) gt 0><span class="error">${order.openTotal?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td><a href="/mfg/control/salesOrder?salesOrderId=${order.salesOrderId}">详情</a></td>
            </tr>
        </#list>
    </table>
    <@pg.pager action="/mfg/control/salesOrders" query=('search=' + ((or.search)!'')?url + '&' + 'statusId=' + ((or.statusId)!'')?url)
    page=(or.page)!1 pageCount=(or.pageCount)!1 total=(or.totalCount)!0 unit="条" pageSize=(or.pageSize)!20/>
<#else>
    <p class="hint">还没有销售订单。上面填客户与型号数量就能建一张；下单时先把库里已有的成品锁给客户。</p>
</#if>
