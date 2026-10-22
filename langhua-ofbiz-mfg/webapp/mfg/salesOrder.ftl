<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  销售订单详情：明细行（数量 / 已预留 / 已出货 / 欠货 / 派生工单），预留流水，以及三个动作
  （先锁在库、把不够的排产、关闭释放预留）和按订单出货。
  数据：orderDetail.order、orderDetail.items、orderDetail.reservations
-->
<#assign od = (orderDetail)!{}>
<#assign so = (od.order)!{}>
<#assign items = (od.items)![]>
<#assign reservations = (od.reservations)![]>
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

<#if !so.salesOrderId??>
    <h1>订单不存在</h1>
    <p><a href="/mfg/control/salesOrders">← 返回订单列表</a></p>
<#else>
    <h1>${so.salesOrderId} · ${so.customerName!}</h1>
    <p data-status="${so.statusId!}">
        状态 <b>${soLabel(so.statusId)}</b>
        <#if so.customerPo??>客户订单号 ${so.customerPo}</#if>
        <#if so.orderDate??> / 下单 ${so.orderDate?string("yyyy-MM-dd")}</#if>
        <#if so.dueDate??> / 交期 ${so.dueDate?string("yyyy-MM-dd")}</#if>
    </p>
    <p>
        数量 <b>${(so.qtyTotal!0)?string("0.##")}</b>
        &nbsp; 已预留 <b>${(so.reservedTotal!0)?string("0.##")}</b>
        &nbsp; 已出货 <b>${(so.shippedTotal!0)?string("0.##")}</b>
        &nbsp; 欠货 <#if (so.openTotal!0) gt 0><b class="error">${so.openTotal?string("0.##")}</b><#else><b>0</b></#if>
    </p>

    <#if errList?has_content>
        <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
    </#if>

    <p>
        <form method="post" action="/mfg/control/salesOrderReserve" class="inline">
            <input type="hidden" name="salesOrderId" value="${so.salesOrderId}"/>
            <input type="submit" value="先锁在库"/>
        </form>
        <form method="post" action="/mfg/control/salesOrderPlan" class="inline">
            <input type="hidden" name="salesOrderId" value="${so.salesOrderId}"/>
            <input type="submit" value="排产（差的部分开工单）"/>
        </form>
        <form method="post" action="/mfg/control/salesOrderClose" class="inline">
            <input type="hidden" name="salesOrderId" value="${so.salesOrderId}"/>
            <input type="submit" value="关闭订单"/>
        </form>
        <span class="hint">「先锁在库」按批次先后把成品预留到这张单上；「排产」只给还差的数量开工单，
            工单完工入库后会自动把新成品锁回这张单；关闭订单会把预留全部还给库存。</span>
    </p>

    <h2>订单明细</h2>
    <#if items?has_content>
        <table id="orderItemTable">
            <tr>
                <th class="num">行</th><th>型号</th><th class="num">数量</th><th class="num">已预留</th>
                <th class="num">已出货</th><th class="num">欠货</th><th>工单</th><th>交期</th>
            </tr>
            <#list items as item>
                <tr>
                    <td class="num">${item.lineSeq?string("0")}</td>
                    <td>${item.productCode!}</td>
                    <td class="num">${(item.qty!0)?string("0.##")}</td>
                    <td class="num"><#if (item.reservedQty!0) gt 0><span class="warn">${item.reservedQty?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                    <td class="num">${(item.shippedQty!0)?string("0.##")}</td>
                    <td class="num"><#if (item.openQty!0) gt 0><span class="error">${item.openQty?string("0.##")}</span><#else>-</#if></td>
                    <td><#if item.workOrderId??><a href="/mfg/control/workOrder?workOrderId=${item.workOrderId}">${item.workOrderId}</a><#else><span class="hint">未排产</span></#if></td>
                    <td><#if item.lineDueDate??>${item.lineDueDate?string("yyyy-MM-dd")}</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">这张单还没有明细行。</p>
    </#if>

    <h2>预留流水</h2>
    <#if reservations?has_content>
        <table id="orderReservationTable">
            <tr><th>时间</th><th>批次</th><th>型号</th><th class="num">行</th>
                <th class="num">变动</th><th>类型</th><th>备注</th></tr>
            <#list reservations as r>
                <tr>
                    <td><#if r.effectiveDate??>${r.effectiveDate?string("MM-dd HH:mm")}</#if></td>
                    <td><a href="/mfg/control/stockMoves?lotId=${r.lotId?url}">${r.lotId!}</a></td>
                    <td>${r.productCode!}</td>
                    <td class="num"><#if r.lineSeq??>${r.lineSeq?string("0")}</#if></td>
                    <td class="num"><#if ((r.reservedDiff)!0) lt 0><span class="error">${r.reservedDiff?string("0.##")}</span><#else><span class="ok">+${r.reservedDiff?string("0.##")}</span></#if></td>
                    <td><#if (r.movementTypeId!'') == 'MFG_MOVE_RELEASE'>释放<#else>预留</#if></td>
                    <td>${r.note!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有预留——点上面的「先锁在库」，或者先排产、等完工入库后自动锁给这张单。</p>
    </#if>

    <fieldset>
        <legend>按这张订单出货</legend>
        <form method="post" action="/mfg/control/deliverySave">
            <input type="hidden" name="salesOrderId" value="${so.salesOrderId}"/>
            <input type="hidden" name="customerName" value="${so.customerName!}"/>
            <p>
                客户 ${so.customerName!}
                出货日 <input type="text" name="deliveryDate" size="10" placeholder="yyyy-mm-dd"/>
                快递 <input type="text" name="carrier" size="8"/>
                单号 <input type="text" name="trackingNo" size="14"/>
            </p>
            <p>
                SN <input type="text" name="sns" size="60" placeholder="空格或逗号分隔，留空则按工单+数量出货"/>
                或 工单 <input type="text" name="workOrderId" size="16" placeholder="WO-..."/>
                数量 <input type="text" name="qty" size="5"/>
            </p>
            <p class="hint">只发已完工入库的 SN；出货会从成品批次扣减，并按本单明细核销、释放对应预留。</p>
            <p><input type="submit" value="出货"/></p>
        </form>
    </fieldset>

    <fieldset>
        <legend>修改明细</legend>
        <form method="post" action="/mfg/control/salesOrderSave">
            <input type="hidden" name="salesOrderId" value="${so.salesOrderId}"/>
            <input type="hidden" name="customerName" value="${so.customerName!}"/>
            <input type="hidden" name="customerPo" value="${so.customerPo!}"/>
            <input type="hidden" name="orderDate" value="<#if so.orderDate??>${so.orderDate?string("yyyy-MM-dd")}</#if>"/>
            <input type="hidden" name="dueDate" value="<#if so.dueDate??>${so.dueDate?string("yyyy-MM-dd")}</#if>"/>
            <p><textarea name="lines" rows="4" cols="80"><#list items as item>${item.productCode},${item.qty?string("0.##")}<#if item.lineDueDate??>,${item.lineDueDate?string("yyyy-MM-dd")}</#if>
</#list></textarea></p>
            <p class="hint">行的顺序就是行号；删掉一行会同时释放它占的预留。</p>
            <p><input type="submit" value="保存明细"/></p>
        </form>
    </fieldset>

    <p><a href="/mfg/control/salesOrders">← 返回订单列表</a></p>
</#if>
