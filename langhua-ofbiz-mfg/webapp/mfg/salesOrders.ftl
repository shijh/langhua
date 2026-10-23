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

<p id="listHint" class="hint"></p>
<div id="orderListBlock">
<#if orders?has_content>
    <table id="orderTable">
        <tr>
            <th>单号</th><th>客户</th><th>客户订单号</th><th>下单</th><th>交期</th><th>状态</th>
            <th class="num">行数</th><th class="num">数量</th><th class="num">已预留</th>
            <th class="num">已出货</th><th class="num">欠货</th><th>操作</th>
        </tr>
        <#list orders as order>
            <#assign soKey = 'so-' + (order.salesOrderId!'')>
            <#assign soLines = (order.items)![]>
            <tr<#if (order.statusId!'') == 'MFG_SO_SHIPPED'> class="okRow"</#if> data-status="${order.statusId!}">
                <td><a href="/mfg/control/salesOrder?salesOrderId=${order.salesOrderId}">${order.salesOrderId}</a></td>
                <td>${order.customerName!}</td>
                <td>${order.customerPo!}</td>
                <td><#if order.orderDate??>${order.orderDate?string("MM-dd")}</#if></td>
                <td><#if order.dueDate??>${order.dueDate?string("MM-dd")}</#if></td>
                <td><#if (order.statusId!'') == 'MFG_SO_SHIPPED'><span class="ok">${soLabel(order.statusId)}</span><#elseif (order.statusId!'') == 'MFG_SO_PLANNED'><span class="warn">${soLabel(order.statusId)}</span><#else>${soLabel(order.statusId)}</#if></td>
                <td class="num"><#if soLines?has_content><a href="#" data-toggle-lines="${soKey}" data-open="N">${soLines?size} <span>▸</span></a><#else>0</#if></td>
                <td class="num">${(order.qtyTotal!0)?string("0.##")}</td>
                <td class="num"><#if (order.reservedTotal!0) gt 0><span class="warn">${order.reservedTotal?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td class="num">${(order.shippedTotal!0)?string("0.##")}</td>
                <td class="num"><#if (order.openTotal!0) gt 0><span class="error">${order.openTotal?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td><a href="/mfg/control/salesOrder?salesOrderId=${order.salesOrderId}">详情</a></td>
            </tr>
            <#if soLines?has_content>
                <tr data-lines="${soKey}"><td colspan="12" class="lines">
                    <table class="inner" data-order-lines="${order.salesOrderId}">
                        <tr>
                            <th>行</th><th>型号</th><th class="num">数量</th><th class="num">已预留</th>
                            <th class="num">已出货</th><th class="num">欠货</th><th>工单</th><th></th>
                        </tr>
                        <#list soLines as line>
                            <#assign lineForm = 'soLine_' + (order.salesOrderId!'') + '_' + (line.lineSeq?c)>
                            <tr data-line-seq="${line.lineSeq}">
                                <td>${line.lineSeq}</td>
                                <td>${line.productCode!}</td>
                                <td class="num"><input class="narrow" type="text" name="qty" value="${(line.qty!0)?string("0.##")}" form="${lineForm}"/></td>
                                <td class="num">${(line.reservedQty!0)?string("0.##")}</td>
                                <td class="num">${(line.shippedQty!0)?string("0.##")}</td>
                                <td class="num"><#if (line.openQty!0) gt 0><span class="error">${line.openQty?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                                <td><#if (line.workOrderId)?has_content><a href="/mfg/control/workOrder?workOrderId=${line.workOrderId}">${line.workOrderId}</a><#else><span class="hint">没排产</span></#if></td>
                                <td><button type="submit" form="${lineForm}">保存</button></td>
                            </tr>
                        </#list>
                    </table>
                    <#list soLines as line>
                        <#assign lineForm = 'soLine_' + (order.salesOrderId!'') + '_' + (line.lineSeq?c)>
                        <form id="${lineForm}" data-order-line method="post" action="/mfg/control/salesOrderLineJson">
                            <input type="hidden" name="salesOrderId" value="${order.salesOrderId}"/>
                            <input type="hidden" name="lineSeq" value="${line.lineSeq}"/>
                        </form>
                    </#list>
                    <p class="hint">改数量后点「保存」，欠货会跟着重算；数量不能小于已出货数。要加行、改交期请进详情页。</p>
                </td></tr>
            </#if>
        </#list>
    </table>
    <@pg.pager action="/mfg/control/salesOrders" query=('search=' + ((or.search)!'')?url + '&' + 'statusId=' + ((or.statusId)!'')?url)
    page=(or.page)!1 pageCount=(or.pageCount)!1 total=(or.totalCount)!0 unit="条" pageSize=(or.pageSize)!20/>
<#else>
    <p class="hint">还没有销售订单。上面填客户与型号数量就能建一张；下单时先把库里已有的成品锁给客户。</p>
</#if>
</div>

<#-- 展开的那一行改完数量后就地保存，只把上面的块换掉，不刷新整页；fetch 挂了就退回普通提交 -->
<script>
(function () {
    var hint = document.getElementById('listHint');
    var block = document.getElementById('orderListBlock');

    function say(ok, message) {
        if (!hint) {
            return;
        }
        hint.className = ok ? 'ok' : 'error';
        hint.textContent = message || '';
        hint.setAttribute('data-ok', ok ? 'Y' : 'N');
    }

    function openKeys() {
        var keys = [];
        if (!block) {
            return keys;
        }
        var links = block.querySelectorAll('[data-toggle-lines][data-open="Y"]');
        for (var i = 0; i < links.length; i++) {
            keys.push(links[i].getAttribute('data-toggle-lines'));
        }
        return keys;
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form || !form.hasAttribute || !form.hasAttribute('data-order-line')) {
            return;
        }
        event.preventDefault();
        var opened = openKeys();
        fetch(form.getAttribute('action'), {
            method: 'POST',
            credentials: 'same-origin',
            body: new URLSearchParams(new FormData(form))
        }).then(function (answer) {
            return answer.json();
        }).then(function (data) {
            say(data.ok, data.message);
            if (!data.ok || !block) {
                return null;
            }
            return fetch(location.href, {credentials: 'same-origin'}).then(function (answer) {
                return answer.text();
            }).then(function (html) {
                var doc = new DOMParser().parseFromString(html, 'text/html');
                var fresh = doc.getElementById('orderListBlock');
                if (!fresh) {
                    return;
                }
                block.innerHTML = fresh.innerHTML;
                for (var i = 0; i < opened.length; i++) {
                    var link = block.querySelector('[data-toggle-lines="' + opened[i] + '"]');
                    if (link) {
                        link.click();
                    }
                }
            });
        }).catch(function () {
            form.submit();
        });
    }, true);
})();
</script>
