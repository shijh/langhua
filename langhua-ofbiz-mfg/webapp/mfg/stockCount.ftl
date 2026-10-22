<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  盘点：逐批填实盘数，系统算出盘盈/盘亏并记一笔流水；下面列最近盘点记录。
  数据：lotResult.lots（lotId / componentCode / receivedQty / onHandQty / availableQty /
        inspectStatusId / supplierLot / receivedDate）、countResult.counts
-->
<#assign ll = (lotResult)!{}>
<#assign lots = (ll.lots)![]>
<#assign cl = (countResult)!{}>
<#assign counts = (cl.counts)![]>
<#assign errList = (errorMessageList)![]>

<h1>盘点</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<form method="get" action="/mfg/control/stockCount">
    搜索 <input type="text" name="search" size="18" value="${(ll.search)!''}" placeholder="料号 / 批次 / 供应商批号"/>
    <input type="submit" value="查询"/>
    <a href="/mfg/control/stock">返回结存</a>
    <a href="/mfg/control/stockMoves">出入库流水</a>
</form>
<p class="hint">盘盈盘亏会写成一条「盘盈亏」流水，不是把数字直接改掉——账和实物各说各话时，能看出差多少、谁盘的。</p>

<#if lots?has_content>
    <table id="countTable">
        <tr>
            <th>批次</th><th>类别</th><th>料号</th><th>供应商批号</th><th>到货</th><th class="num">在库</th>
            <th class="num">可发</th><th>来料检验</th><th>实盘数</th>
        </tr>
        <#list lots as lot>
            <tr>
                <td>${lot.lotId}</td>
                <td><#if (lot.lotKindId!'') == 'MFG_LOT_PRODUCT'>成品<#else>元件</#if></td>
                <td>${lot.componentCode!}</td>
                <td>${lot.supplierLot!}</td>
                <td><#if lot.receivedDate??>${lot.receivedDate?string("MM-dd")}</#if></td>
                <td class="num">${(lot.onHandQty!0)?string("0.##")}</td>
                <td class="num">${(lot.availableQty!0)?string("0.##")}</td>
                <td><#if (lot.inspectStatusId!'') == 'MFG_IQC_FAIL'><span class="error">不合格</span><#elseif (lot.inspectStatusId!'') == 'MFG_IQC_PASS'><span class="ok">合格</span><#elseif (lot.inspectStatusId!'') == 'MFG_IQC_CONCESSION'>让步接收<#else>待检</#if></td>
                <td>
                    <form method="post" action="/mfg/control/stockCountSave" class="inline">
                        <input type="hidden" name="lotId" value="${lot.lotId}"/>
                        数量 <input type="text" name="countedQty" size="8"/>
                        盘点人 <input type="text" name="counterName" size="8"/>
                        <input type="submit" value="登记"/>
                    </form>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">没有可盘的批次——先到「物料批次」登记到货。</p>
</#if>

<h3>最近盘点记录（${counts?size} 条）</h3>
<#if counts?has_content>
    <table id="countList">
        <tr>
            <th>盘点单</th><th>日期</th><th>批次</th><th>类别</th><th>料号</th>
            <th class="num">账面</th><th class="num">实盘</th><th class="num">差异</th><th>盘点人</th><th>备注</th>
        </tr>
        <#list counts as count>
            <tr>
                <td>${count.physicalInventoryId}</td>
                <td><#if count.countDate??>${count.countDate?string("yyyy-MM-dd")}</#if></td>
                <td>${count.lotId}</td>
                <td><#if (count.lotKindId!'') == 'MFG_LOT_PRODUCT'>成品<#else>元件</#if></td>
                <td>${count.componentCode!}</td>
                <td class="num">${(count.bookQty!0)?string("0.##")}</td>
                <td class="num">${(count.countedQty!0)?string("0.##")}</td>
                <td class="num"><#if (count.varianceQty!0) gt 0><span class="ok">+${count.varianceQty?string("0.##")}</span><#elseif (count.varianceQty!0) lt 0><span class="error">${count.varianceQty?string("0.##")}</span><#else>0</#if></td>
                <td>${count.counterName!}</td>
                <td>${count.note!}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没盘过。填一个实盘数点「登记」，差异会自动写进流水。</p>
</#if>
