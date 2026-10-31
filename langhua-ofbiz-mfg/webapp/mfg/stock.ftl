<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  库存结存：按料号看在库/可发/待检/不合格、金额、安全库存与建议采购。
  数据：stock.rows（componentCode / componentName / onHandQty / availableQty / pendingQty /
        failedQty / value / lotCount / minimumStock / reorderQty / suggestedQty / belowMinimum /
        unitOfMeasure / supplierName）、stock.componentCount / belowCount / onHandTotal / valueTotal
-->
<#assign s = (stock)!{}>
<#assign rows = (s.rows)![]>
<#assign errList = (errorMessageList)![]>

<h1>库存结存</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p class="cards">
    <span class="card"><span class="label">料号</span><span class="value">${(s.componentCount)!0}</span></span>
    <span class="card"><span class="label">低于安全库存</span>
        <span class="value"><#if ((s.belowCount)!0) gt 0><span class="error">${s.belowCount}</span><#else>0</#if></span></span>
    <span class="card"><span class="label">在库合计</span><span class="value">${((s.onHandTotal)!0)?string("0.##")}</span></span>
    <span class="card"><span class="label">库存金额</span><span class="value">${((s.valueTotal)!0)?string("0.00")}</span></span>
</p>

<form method="get" action="/mfg/control/stock">
    搜索 <input type="text" name="search" size="18" value="${(s.search)!''}" placeholder="料号 / 名称"/>
    <label><input type="checkbox" name="belowOnly" value="Y"<#if (parameters.belowOnly!'') == 'Y'> checked="checked"</#if>/> 只看低于安全库存</label>
    <input type="submit" value="查询"/>
    <a href="/mfg/control/stockMoves">出入库流水</a>
    <a href="/mfg/control/stockCount">盘点</a>
</form>

<#if rows?has_content>
    <table id="stockTable">
        <tr>
            <th>料号</th><th>名称</th><th class="num">在库</th><th class="num">已预留</th><th class="num">可发</th>
            <th class="num">待检</th><th class="num">不合格</th><th class="num">批次</th>
            <th class="num">安全库存</th><th class="num">建议采购</th><th class="num">金额</th>
            <th>供应商</th><th>操作</th>
        </tr>
        <#list rows as row>
            <tr<#if row.belowMinimum> class="warnRow"</#if>>
                <td>${row.componentCode}
                    <#-- the shop's own photo of the part, cut out of the order screenshot it came in with -->
                    <#if (row.scanShotId!'')?has_content><br/><a
                        href="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${row.scanShotId?url}"
                        target="_blank"><img class="shot" data-part-shot="${row.scanShotId}"
                        alt="" src="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${row.scanShotId?url}&amp;thumb=1"/></a></#if>
                </td>
                <td>${row.componentName!}</td>
                <td class="num"><#if (row.onHandQty!0) lt 0><span class="error">${row.onHandQty?string("0.##")}</span><#else>${row.onHandQty?string("0.##")}</#if></td>
                <td class="num"><#if (row.reservedQty!0) gt 0><span class="warn">${row.reservedQty?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td class="num">${row.availableQty?string("0.##")}</td>
                <td class="num"><#if (row.pendingQty!0) gt 0>${row.pendingQty?string("0.##")}<#else><span class="hint">—</span></#if></td>
                <td class="num"><#if (row.failedQty!0) gt 0><span class="error">${row.failedQty?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td class="num">${row.lotCount}</td>
                <td class="num"><#if (row.minimumStock!0) gt 0>${row.minimumStock?string("0.##")}<#else><span class="hint">未设</span></#if></td>
                <td class="num"><#if (row.suggestedQty!0) gt 0><span class="warn">${row.suggestedQty?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td class="num"><#if (row.value!0) gt 0>${row.value?string("0.00")}<#else><span class="hint">—</span></#if></td>
                <td>${row.supplierName!}</td>
                <td>
                    <a href="/mfg/control/stockMoves?componentCode=${row.componentCode?url}">流水</a>
                    <a href="/mfg/control/materials?componentCode=${row.componentCode?url}">批次</a>
                    <#-- datasheets and drawings hang on the part number, not on one batch of it: with a
                         file the row opens the file, without one it opens the upload page -->
                    <#if (row.partDocSeq!'')?has_content>
                        <a data-part-docs="${row.componentCode}" data-part-doc-view="${row.partDocSeq}"
                           target="_blank" title="${(row.partDocName)!'资料'}"
                           href="/mfg/control/attachment?ownerTypeId=MFG_ATT_PART&amp;ownerId=${row.componentCode?url}&amp;seq=${row.partDocSeq}">看资料</a>
                        <a class="hint" data-part-docs-open="${row.componentCode}"
                           href="/mfg/control/partDocs?componentCode=${row.componentCode?url}"
                           title="上传新版 / 删除传错的资料">维护<#if ((row.partDocCount)!0) gt 1>（${row.partDocCount}）</#if></a>
                    <#else>
                        <a data-part-docs="${row.componentCode}" href="/mfg/control/partDocs?componentCode=${row.componentCode?url}">资料</a>
                    </#if>
                </td>
            </tr>
        </#list>
    </table>
    <@pg.pager action="/mfg/control/stock" query=('search=' + ((s.search)!'')?url + '&' + 'belowOnly=' + ((parameters.belowOnly)!''))
    page=(s.page)!1 pageCount=(s.pageCount)!1 total=(s.totalCount)!0 unit="条" pageSize=(s.pageSize)!20/>
<#else>
    <p class="hint">还没有库存。到「物料批次」登记到货，或从 CSV 批量导入；有到货之后这里按料号汇总。</p>
</#if>

<fieldset>
    <legend>安全库存 / 料号档案</legend>
    <form method="post" action="/mfg/control/stockSettingSave">
        <p>
            料号 <input type="text" name="componentCode" size="14" placeholder="必填"/>
            名称 <input type="text" name="partName" size="20"/>
            单位 <input type="text" name="unitOfMeasure" size="6" placeholder="个/盘/米"/>
            安全库存 <input type="text" name="minimumStock" size="8"/>
            补货量 <input type="text" name="reorderQty" size="8"/>
            供应商 <input type="text" name="supplierName" size="14"/>
            <input type="submit" value="保存"/>
        </p>
        <p class="hint">低于安全库存时，看板告警里会出现这一行；建议采购量 = 补货量，没填就是「安全库存 − 在库」。<br/>
            可发 = 在库 − 已预留（不合格批次为 0）；预留由工单开工时自动做，也可在工单页手动预留/释放。</p>
    </form>
</fieldset>

<#--
  成品库存：工单完工入库的成品批次 FG-WO-…，按型号汇总。
  数据：productStock.rows（productCode / productName / onHandQty / reservedQty / availableQty /
        shippedQty / value / lotCount / lastCompletedDate）
-->
<#assign ps = (productStock)!{}>
<#assign prow = (ps.rows)![]>

<h2>成品库存</h2>
<p class="cards">
    <span class="card"><span class="label">型号</span><span class="value">${(ps.productCount)!0}</span></span>
    <span class="card"><span class="label">成品在库</span><span class="value">${((ps.onHandTotal)!0)?string("0.##")}</span></span>
    <span class="card"><span class="label">累计出货</span><span class="value">${((ps.shippedTotal)!0)?string("0.##")}</span></span>
    <span class="card"><span class="label">成品金额</span><span class="value">${((ps.valueTotal)!0)?string("0.00")}</span></span>
</p>
<#if prow?has_content>
    <table id="productStockTable">
        <tr>
            <th>型号</th><th>名称</th><th class="num">在库</th><th class="num">已预留</th>
            <th class="num">可发</th><th class="num">累计出货</th><th class="num">批次</th>
            <th class="num">金额</th><th>最近入库</th><th>操作</th>
        </tr>
        <#list prow as row>
            <tr>
                <td>${row.productCode}</td>
                <td>${row.productName!}</td>
                <td class="num">${(row.onHandQty!0)?string("0.##")}</td>
                <td class="num"><#if (row.reservedQty!0) gt 0><span class="warn">${row.reservedQty?string("0.##")}</span><#else><span class="hint">—</span></#if></td>
                <td class="num">${(row.availableQty!0)?string("0.##")}</td>
                <td class="num">${(row.shippedQty!0)?string("0.##")}</td>
                <td class="num">${row.lotCount}</td>
                <td class="num"><#if (row.value!0) gt 0>${row.value?string("0.00")}<#else><span class="hint">—</span></#if></td>
                <td>${(row.lastCompletedDate?string("yyyy-MM-dd"))!}</td>
                <td>
                    <a href="/mfg/control/stockMoves?componentCode=${row.productCode?url}&kind=MFG_LOT_PRODUCT">流水</a>
                    <a href="/mfg/control/main?search=${row.productCode?url}">工单</a>
                </td>
            </tr>
        </#list>
    </table>
    <p class="hint">成品在完工入库时按工单建批次 <code>FG-WO-…</code>，单位成本 = 该工单实际成本 ÷ 合格数；
        出货时自动从该批次扣减，所以「在库」= 完工未出货。</p>
<#else>
    <p class="hint">还没有成品入库。到工单页点「完工入库」，报工过的合格数就会进成品库存。</p>
</#if>
