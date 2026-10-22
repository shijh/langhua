<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  良率 / 工时趋势报表：按日、周、月把报工汇总，看良品率与单台工时怎么变。
  数据：trend.rows（period / qtyOk / qtyNg / yieldRate / hours / hoursPerUnit / orderCount）、
        trend.yieldRate / trend.hoursPerUnit / trend.qtyOkTotal / trend.qtyNgTotal / trend.hoursTotal /
        trend.fromDate / trend.toDate / trend.groupBy / trend.worstRow / trend.rowCount
-->
<#assign t = (trend)!{}>
<#assign rows = (t.rows)![]>
<#assign groupBy = (t.groupBy)!'MONTH'>
<#assign worst = (t.worstRow)!{}>
<#assign errList = (errorMessageList)![]>

<#function groupLabel key>
    <#if (key!'') == 'DAY'><#return '按日'>
    <#elseif (key!'') == 'WEEK'><#return '按周'>
    <#else><#return '按月'>
    </#if>
</#function>

<h1>报表：良率与工时趋势</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<form method="get" action="/mfg/control/reports">
    起 <input type="text" name="fromDate" size="11" value="<#if t.fromDate??>${t.fromDate?string("yyyy-MM-dd")}</#if>" placeholder="yyyy-mm-dd"/>
    止 <input type="text" name="toDate" size="11" value="<#if t.toDate??>${t.toDate?string("yyyy-MM-dd")}</#if>" placeholder="yyyy-mm-dd"/>
    汇总
    <select name="groupBy">
        <option value="DAY"<#if groupBy == 'DAY'> selected="selected"</#if>>按日</option>
        <option value="WEEK"<#if groupBy == 'WEEK'> selected="selected"</#if>>按周</option>
        <option value="MONTH"<#if groupBy == 'MONTH'> selected="selected"</#if>>按月</option>
    </select>
    <input type="submit" value="查询"/>
    <span class="hint">留空 = 最近 180 天</span>
</form>

<p class="cards">
    <span class="card"><span class="label">区间</span>
        <span class="value"><#if t.fromDate??>${t.fromDate?string("yyyy-MM-dd")}</#if> ~ <#if t.toDate??>${t.toDate?string("yyyy-MM-dd")}</#if></span></span>
    <span class="card"><span class="label">良品 / 不良</span>
        <span class="value">${(t.qtyOkTotal)!0} / ${(t.qtyNgTotal)!0}</span></span>
    <span class="card"><span class="label">良率</span>
        <span class="value"><#if ((t.qtyOkTotal)!0) + ((t.qtyNgTotal)!0) gt 0>${((t.yieldRate)!0)?string("0.0")}%<#else>—</#if></span></span>
    <span class="card"><span class="label">总工时</span>
        <span class="value">${((t.hoursTotal)!0)?string("0.##")} h</span></span>
    <span class="card"><span class="label">单台工时</span>
        <span class="value"><#if ((t.qtyOkTotal)!0) gt 0>${((t.hoursPerUnit)!0)?string("0.##")} h<#else>—</#if></span></span>
    <span class="card"><span class="label">最差期间</span>
        <span class="value"><#if worst.period??>${worst.period}（${worst.yieldRate?string("0.0")}%）<#else>—</#if></span></span>
</p>

<p>
    <a class="download" href="/mfg/control/yieldCsv?fromDate=<#if t.fromDate??>${t.fromDate?string("yyyy-MM-dd")}</#if>&amp;toDate=<#if t.toDate??>${t.toDate?string("yyyy-MM-dd")}</#if>&amp;groupBy=${groupBy}">导出良率趋势 CSV</a>
    <a class="download" href="/mfg/control/hoursCsv?fromDate=<#if t.fromDate??>${t.fromDate?string("yyyy-MM-dd")}</#if>&amp;toDate=<#if t.toDate??>${t.toDate?string("yyyy-MM-dd")}</#if>&amp;groupBy=${groupBy}">导出工时趋势 CSV</a>
    <a class="download" href="/learningcurve/control/main?mesGroupBy=${groupBy}&amp;mesFrom=<#if t.fromDate??>${t.fromDate?string("yyyy-MM-dd")}</#if>&amp;mesTo=<#if t.toDate??>${t.toDate?string("yyyy-MM-dd")}</#if>">喂进学习曲线</a>
    <span class="hint">两份 CSV 都带 UTF-8 BOM，Excel 双击直接开；数字用小数点、不用千分位。<br/>
        最后那个链接会把这张表按期间的良品数与工时直接带进学习曲线页（同一个汇总口径），不用手填 <code>数量:工时</code>。</span>
</p>

<#if rows?has_content>
    <table id="trendTable">
        <tr>
            <th>期间（${groupLabel(groupBy)}）</th><th class="num">良品</th><th class="num">不良</th>
            <th class="num">良率</th><th class="num">工时</th><th class="num">单台工时</th><th class="num">工单数</th>
        </tr>
        <#list rows as row>
            <tr>
                <td>${row.period}</td>
                <td class="num">${row.qtyOk?string("0")}</td>
                <td class="num">${row.qtyNg?string("0")}</td>
                <td class="num"><#if (row.yieldRate!100) lt 95><span class="warn">${row.yieldRate?string("0.0")}%</span><#else><span class="ok">${row.yieldRate?string("0.0")}%</span></#if></td>
                <td class="num">${row.hours?string("0.##")}</td>
                <td class="num"><#if (row.hoursPerUnit!0) gt 0>${row.hoursPerUnit?string("0.##")}<#else><span class="hint">—</span></#if></td>
                <td class="num">${row.orderCount}</td>
            </tr>
        </#list>
    </table>
    <p class="hint">良率 = 良品 ÷（良品 + 不良）；单台工时 = 报工工时 ÷ 良品，可以拿去和学习曲线组件的实测点对照。</p>
<#else>
    <p class="hint">这段时间没有报工记录。先到工单详情页「报工」，报表才有数。</p>
</#if>
