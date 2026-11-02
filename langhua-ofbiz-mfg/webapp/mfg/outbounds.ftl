<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  出库单一览：库管员干活的那一页。单子是「产品开发 → 研发 BOM」按某一版写出来的领料清单，
  但料是在货架上捡的，所以这张表挂在「库存」下面（Step 246）。
  数据：outboundResult.rows（outboundId / projectCode / bomVersion / outboundDate / lineCount /
        totalQty / issuedQty / operatorName / receiverName / note / cancelReason / issued / cancelled）
-->
<#assign lr = (outboundResult)!{}>
<#assign rows = (lr.rows)![]>
<#assign statusId = (lr.statusId)!''>
<#assign errList = (errorMessageList)![]>

<h1>出库单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p class="hint">单子是在「产品开发 → 研发 BOM」的「出库单」那一段生成的：按当前那一版写清单，只写还没领过的差额。
    捡完料回到这张单上点「按这张单出库」，库存按先进先出扣减，同时把本项目的锁料放掉同样数量。</p>

<form method="get" action="/mfg/control/outbounds">
    关键词 <input type="text" name="search" size="18" value="${(lr.search)!''}" placeholder="单号 / 项目 / 领料人 / 备注"/>
    状态
    <select name="statusId">
        <option value=""<#if statusId == ''> selected="selected"</#if>>全部</option>
        <option value="MFG_OUT_DRAFT"<#if statusId == 'MFG_OUT_DRAFT'> selected="selected"</#if>>待出库</option>
        <option value="MFG_OUT_ISSUED"<#if statusId == 'MFG_OUT_ISSUED'> selected="selected"</#if>>已出库</option>
        <option value="MFG_OUT_CANCELLED"<#if statusId == 'MFG_OUT_CANCELLED'> selected="selected"</#if>>已作废</option>
    </select>
    <input type="submit" value="查询"/>
    <a href="/mfg/control/stock">返回库存结存</a>
    <a href="/mfg/control/stockMoves">出入库流水</a>
</form>

<#if rows?has_content>
    <table id="outboundList">
        <tr>
            <th>单号</th><th>日期</th><th>项目</th><th>BOM 版本</th><th class="num">行数</th>
            <th class="num">合计</th><th class="num">已出</th><th>状态</th><th>制作</th><th>领料人</th><th>备注</th>
        </tr>
        <#list rows as o>
            <tr data-outbound-row="${o.outboundId}"
                data-outbound-status="<#if o.issued>ISSUED<#elseif o.cancelled>CANCELLED<#else>DRAFT</#if>"
                data-outbound-receiver="${(o.receiverName)!}">
                <td><a href="/mfg/control/outbound?outboundId=${o.outboundId}">${o.outboundId}</a></td>
                <td><#if o.outboundDate??>${o.outboundDate?string("yyyy-MM-dd")}</#if></td>
                <td><#if (o.projectCode!'')?has_content><a href="/mfg/control/dev?projectCode=${o.projectCode?url}#devoutbound">${o.projectCode}</a></#if></td>
                <td>${(o.bomVersion)!}</td>
                <td class="num">${(o.lineCount)!0}</td>
                <td class="num">${(o.totalQty!0)?string("0.##")}</td>
                <td class="num">${(o.issuedQty!0)?string("0.##")}</td>
                <td><#if o.issued><span class="ok">已出库</span><#elseif o.cancelled><span class="hint"<#if ((o.cancelReason)!'')?has_content> title="作废原因：${o.cancelReason}"</#if>>已作废</span><#else><span class="warn">待出库</span></#if></td>
                <td>${(o.operatorName)!}</td>
                <td>${(o.receiverName)!}</td>
                <td>${(o.note)!}</td>
            </tr>
        </#list>
    </table>
    <@pg.pager action="/mfg/control/outbounds" query=('search=' + ((lr.search)!'')?url + '&statusId=' + statusId?url)
    page=(lr.page)!1 pageCount=(lr.pageCount)!1 total=(lr.totalCount)!0 unit="张" pageSize=(lr.pageSize)!20/>
<#else>
    <p class="hint"><#if ((lr.search)!'') != ''>没有匹配的单：换一个单号、项目号或领料人试试。
        <#elseif statusId == 'MFG_OUT_DRAFT'>现在没有待出库的单。到研发 BOM 里点「转出库单」就会生成一张。
        <#elseif statusId == 'MFG_OUT_ISSUED'>还没有出过库的单。
        <#elseif statusId == 'MFG_OUT_CANCELLED'>没有作废的单。
        <#else>还没有出库单：到「产品开发 → 研发 BOM」点「转出库单」。</#if></p>
</#if>
