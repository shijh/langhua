<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Material shortage warning: open work orders ordered by due date with the lines that are short.
  Data: shortage.orders, shortage.orderCount, shortage.shortageOrderCount
-->
<#assign sh = (shortage)!{}>
<#assign orders = (sh.orders)![]>
<#assign errList = (errorMessageList)![]>

<h1>齐套预警</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p>
    在制工单 ${(sh.orderCount)!0} 张，其中 <span class="big error">${(sh.shortageOrderCount)!0}</span> 张欠料。
    <span class="hint">需求 = BOM 用量 × 计划数量；欠料 = 需求 − 已发料。</span>
    <a class="download" href="/mfg/control/shortageCsv">导出欠料 CSV</a>
</p>

<form method="get" action="/mfg/control/shortage">
    搜索 <input type="text" name="search" size="24" value="${(sh.search)!''}" placeholder="工单号 / 型号 / 客户"/>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(sh.totalCount)!0} 张</span>
</form>

<#if orders?has_content>
    <table>
        <tr>
            <th>交期</th><th>工单</th><th>产品型号</th><th class="num">计划数</th>
            <th>客户/项目</th><th>SMT 外协</th><th class="num">欠料行</th>
            <th class="num">欠料总量</th><th>欠料明细（前 5 行）</th>
        </tr>
        <#list orders as order>
            <tr>
                <td><#if order.dueDate??>${order.dueDate?string("yyyy-MM-dd")}<#else>-</#if></td>
                <td><a href="/mfg/control/workOrder?workOrderId=${order.workOrderId}">${order.workOrderId!}</a></td>
                <td>${order.productCode!}</td>
                <td class="num">${(order.plannedQty)?string("0")}</td>
                <td>${order.customerName!}</td>
                <td>${order.smtVendor!}</td>
                <td class="num">
                    <#if ((order.shortageLineCount)!0) gt 0>
                        <span class="error">${order.shortageLineCount}</span>/<#if order.lineCount??>${order.lineCount}</#if>
                    <#else>
                        <span class="ok">0</span>/<#if order.lineCount??>${order.lineCount}</#if>
                    </#if>
                </td>
                <td class="num"><#if ((order.shortageQty)!0) gt 0>${order.shortageQty?string("0.##")}<#else>-</#if></td>
                <td>
                    <#list (order.shortages)![] as line>
                        <#if line_index lt 5>
                            ${line.componentCode!}<#if (line.isKeyPart!'N') == 'Y'> <b>★</b></#if>
                            欠 ${((line.requiredQty)!0 - (line.issuedQty)!0)?string("0.##")}
                            <span class="hint">(需 ${((line.requiredQty)!0)?string("0.##")} / 发 ${((line.issuedQty)!0)?string("0.##")})</span><br/>
                        </#if>
                    </#list>
                    <#if ((order.shortageLineCount)!0) gt 5><span class="hint">…还有 ${order.shortageLineCount - 5} 行</span></#if>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">没有在制工单。</p>
</#if>
<#if (sh.pageCount?default(1)) gt 1>
    <p class="pager">
        第 ${(sh.page)!1} / ${(sh.pageCount)!1} 页（共 ${(sh.totalCount)!0} 张）
        <#if (sh.page?default(1)) gt 1>
            <a href="/mfg/control/shortage?search=${(sh.search)!''?url}&page=1">首页</a>
            <a href="/mfg/control/shortage?search=${(sh.search)!''?url}&page=${(sh.page)-1}">上一页</a>
        </#if>
        <#if (sh.page?default(1)) lt (sh.pageCount?default(1))>
            <a href="/mfg/control/shortage?search=${(sh.search)!''?url}&page=${(sh.page)+1}">下一页</a>
            <a href="/mfg/control/shortage?search=${(sh.search)!''?url}&page=${(sh.pageCount)!1}">末页</a>
        </#if>
    </p>
</#if>

