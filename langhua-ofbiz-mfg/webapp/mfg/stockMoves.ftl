<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  出入库流水：一条一笔，带该批次的结存余额；可按料号 / 批次 / 类型 / 日期过滤。
  数据：ledger.rows（lotId / seqId / componentCode / componentName / movementTypeId /
        quantityDiff / balanceQty / unitCost / workOrderId / physicalInventoryId /
        referenceNo / operatorName / effectiveDate / note）
-->
<#assign lr = (ledger)!{}>
<#assign rows = (lr.rows)![]>
<#assign typeId = (lr.movementTypeId)!''>
<#assign kindId = (lr.kind)!''>
<#assign errList = (errorMessageList)![]>

<#function moveLabel id>
    <#if (id!'') == 'MFG_MOVE_RECEIPT'><#return '到货入库'>
    <#elseif (id!'') == 'MFG_MOVE_ISSUE'><#return '发料'>
    <#elseif (id!'') == 'MFG_MOVE_RETURN'><#return '退料'>
    <#elseif (id!'') == 'MFG_MOVE_SCRAP'><#return '报废'>
    <#elseif (id!'') == 'MFG_MOVE_COUNT'><#return '盘盈亏'>
    <#elseif (id!'') == 'MFG_MOVE_ADJUST'><#return '调整'>
    <#elseif (id!'') == 'MFG_MOVE_TRANSFER'><#return '移库'>
    <#elseif (id!'') == 'MFG_MOVE_RESERVE'><#return '预留'>
    <#elseif (id!'') == 'MFG_MOVE_RELEASE'><#return '释放预留'>
    <#else><#return '${id!}'>
    </#if>
</#function>

<h1>出入库流水</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<form method="get" action="/mfg/control/stockMoves">
    料号 <input type="text" name="componentCode" size="14" value="${(lr.componentCode)!''}"/>
    批次 <input type="text" name="lotId" size="16" value="${(lr.lotId)!''}"/>
    类别
    <select name="kind">
        <option value=""<#if kindId == ''> selected="selected"</#if>>全部</option>
        <option value="MFG_LOT_COMPONENT"<#if kindId == 'MFG_LOT_COMPONENT'> selected="selected"</#if>>元件</option>
        <option value="MFG_LOT_PRODUCT"<#if kindId == 'MFG_LOT_PRODUCT'> selected="selected"</#if>>成品</option>
    </select>
    类型
    <select name="movementTypeId">
        <option value="">全部</option>
        <option value="MFG_MOVE_RECEIPT"<#if typeId == 'MFG_MOVE_RECEIPT'> selected="selected"</#if>>到货入库</option>
        <option value="MFG_MOVE_ISSUE"<#if typeId == 'MFG_MOVE_ISSUE'> selected="selected"</#if>>发料</option>
        <option value="MFG_MOVE_RETURN"<#if typeId == 'MFG_MOVE_RETURN'> selected="selected"</#if>>退料</option>
        <option value="MFG_MOVE_SCRAP"<#if typeId == 'MFG_MOVE_SCRAP'> selected="selected"</#if>>报废</option>
        <option value="MFG_MOVE_COUNT"<#if typeId == 'MFG_MOVE_COUNT'> selected="selected"</#if>>盘盈亏</option>
        <option value="MFG_MOVE_ADJUST"<#if typeId == 'MFG_MOVE_ADJUST'> selected="selected"</#if>>调整</option>
        <option value="MFG_MOVE_TRANSFER"<#if typeId == 'MFG_MOVE_TRANSFER'> selected="selected"</#if>>移库</option>
        <option value="MFG_MOVE_RESERVE"<#if typeId == 'MFG_MOVE_RESERVE'> selected="selected"</#if>>预留</option>
        <option value="MFG_MOVE_RELEASE"<#if typeId == 'MFG_MOVE_RELEASE'> selected="selected"</#if>>释放预留</option>
    </select>
    起 <input type="text" name="fromDate" size="10" value="<#if lr.fromDate??>${lr.fromDate?string("yyyy-MM-dd")}</#if>" placeholder="yyyy-mm-dd"/>
    止 <input type="text" name="toDate" size="10" value="<#if lr.toDate??>${lr.toDate?string("yyyy-MM-dd")}</#if>" placeholder="yyyy-mm-dd"/>
    <input type="submit" value="查询"/>
    <a href="/mfg/control/stock">返回结存</a>
</form>

<#if rows?has_content>
    <table id="ledgerTable">
        <tr>
            <th>时间</th><th>料号</th><th>批次</th><th>类别</th><th>库位</th><th class="num">序号</th><th>类型</th>
            <th class="num">数量</th><th class="num">结存</th><th class="num">单价</th>
            <th>工单 / 单据</th><th>操作人</th><th>备注</th>
        </tr>
        <#list rows as row>
            <tr>
                <td><#if row.effectiveDate??>${row.effectiveDate?string("MM-dd HH:mm")}</#if></td>
                <td>${row.componentCode!}</td>
                <td><a href="/mfg/control/stockMoves?lotId=${row.lotId?url}">${row.lotId}</a></td>
                <td><#if (row.lotKindId!'') == 'MFG_LOT_PRODUCT'>成品<#else>元件</#if></td>
                <td><#if row.locationId??>${row.locationId}<#else><span class="hint">—</span></#if></td>
                <td class="num">${row.seqId?string("0")}</td>
                <td><#if (row.movementTypeId!'') == 'MFG_MOVE_ISSUE' || (row.movementTypeId!'') == 'MFG_MOVE_SCRAP'><span class="error">${moveLabel(row.movementTypeId)}</span><#elseif (row.movementTypeId!'') == 'MFG_MOVE_RECEIPT'><span class="ok">${moveLabel(row.movementTypeId)}</span><#else>${moveLabel(row.movementTypeId)}</#if></td>
                <td class="num"><#if ((row.displayQty)!0) lt 0><span class="error">${row.displayQty?string("0.##")}</span><#else><span class="ok">+${row.displayQty?string("0.##")}</span></#if></td>
                <td class="num">${row.balanceQty?string("0.##")}</td>
                <td class="num"><#if row.unitCost??>${row.unitCost?string("0.0000")}<#else><span class="hint">—</span></#if></td>
                <td><#if row.workOrderId??><a href="/mfg/control/workOrder?workOrderId=${row.workOrderId?url}">${row.workOrderId}</a></#if><#if row.physicalInventoryId??>${row.physicalInventoryId}</#if><#if row.referenceNo??> ${row.referenceNo}</#if></td>
                <td>${row.operatorName!}</td>
                <td>${row.note!}</td>
            </tr>
        </#list>
    </table>
    <#if ((lr.pageCount)!1) gt 1>
        <p class="pager">
            第 ${(lr.page)!1} / ${(lr.pageCount)!1} 页（共 ${(lr.totalCount)!0} 条）
            <#if ((lr.page)!1) gt 1>
                <a href="/mfg/control/stockMoves?componentCode=${(lr.componentCode)!''?url}&lotId=${(lr.lotId)!''?url}&movementTypeId=${typeId?url}&page=${(lr.page)-1}">上一页</a>
            </#if>
            <#if ((lr.page)!1) lt ((lr.pageCount)!1)>
                <a href="/mfg/control/stockMoves?componentCode=${(lr.componentCode)!''?url}&lotId=${(lr.lotId)!''?url}&movementTypeId=${typeId?url}&page=${(lr.page)+1}">下一页</a>
            </#if>
        </p>
    </#if>
<#else>
    <p class="hint">没有符合条件的流水。入库（建批次）会自动记一笔到货；发料、退料、报废、盘点各记一笔，结存等于这些数之和。</p>
</#if>

<#if (lr.componentCode!'') != '' || (lr.lotId!'') != ''>
    <fieldset>
        <legend>手工登一笔（报废 / 退料 / 调整）</legend>
        <form method="post" action="/mfg/control/inventoryMove">
            <input type="hidden" name="componentCode" value="${(lr.componentCode)!''}"/>
            <p>
                批次 <input type="text" name="lotId" size="16" value="${(lr.lotId)!''}" placeholder="LOT-yyMMdd-NN"/>
                类型
                <select name="movementTypeId">
                    <option value="MFG_MOVE_SCRAP">报废</option>
                    <option value="MFG_MOVE_RETURN">退料（可填工单）</option>
                    <option value="MFG_MOVE_RECEIPT">补入库</option>
                    <option value="MFG_MOVE_ADJUST">调整（可填负数）</option>
                </select>
                数量 <input type="text" name="qty" size="8"/>
                工单 <input type="text" name="workOrderId" size="14" placeholder="退料时填"/>
                单号 <input type="text" name="referenceNo" size="12"/>
                操作人 <input type="text" name="operatorName" size="8"/>
                备注 <input type="text" name="note" size="24"/>
                <input type="submit" value="登记"/>
            </p>
        </form>
    </fieldset>
</#if>
