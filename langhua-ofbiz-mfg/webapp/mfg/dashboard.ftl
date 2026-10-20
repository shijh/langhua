<#ftl encoding="UTF-8">
<#--
  Shop floor dashboard: production cards, after sales KPI, a daily bar chart and the cost split.
  Charts are plain HTML/CSS bars on purpose: no chart library, no build step, works offline and
  inside the OFBiz content security policy.
  Data: board.* (mfgDashboard), trends.kpi / trends.days / trends.responsibilities (mfgDashboardTrends)
-->
<#assign b = (board)!{}>
<#assign reports = (b.recentReports)![]>
<#assign yieldRate = (b.todayYield)!0>
<#assign t = (trends)!{}>
<#assign kpi = (t.kpi)!{}>
<#assign days = (t.days)![]>
<#assign resp = (t.responsibilities)![]>
<#macro respLabel code>
    <#if (code!'') == 'HOUSE'>自制
    <#elseif (code!'') == 'SMT'>SMT 外协
    <#elseif (code!'') == 'SUPPLIER'>来料
    <#elseif (code!'') == 'DESIGN'>设计
    <#else>-</#if>
</#macro>

<h1>车间看板</h1>
<p class="hint">最后更新 ${(t.refreshedAt)!'-'}　<span id="refresh-hint">页面每 60 秒自动刷新</span></p>

<div class="cards">
    <div class="card">
        <div class="label">在制工单</div>
        <div class="value">${(b.openOrderCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">在制计划台数</div>
        <div class="value">${(b.openPlannedQty)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">今日工时 (h)</div>
        <div class="value">${(b.todayHours)?string("0.##")}</div>
    </div>
    <div class="card">
        <div class="label">今日良品</div>
        <div class="value">${(b.todayQtyOk)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">今日不良</div>
        <div class="value">${(b.todayQtyNg)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">今日良率</div>
        <div class="value"><#if (b.todayQtyOk)?default(0) + (b.todayQtyNg)?default(0) gt 0>${(yieldRate * 100)?string("0.0")}%<#else>-</#if></div>
    </div>
</div>

<h2>售后（近 30 天）</h2>
<div class="cards">
    <div class="card">
        <div class="label">出货台数</div>
        <div class="value">${(kpi.shippedUnits)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">售后单数</div>
        <div class="value">${(kpi.claimCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">售后发生率</div>
        <div class="value">${(kpi.claimRate)!'-'}%</div>
    </div>
    <div class="card">
        <div class="label">未结售后单</div>
        <div class="value">${(kpi.openClaims)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">售后成本</div>
        <div class="value">${((kpi.costTotal)!0)?string("0.00")}</div>
    </div>
    <div class="card">
        <div class="label">单台售后成本</div>
        <div class="value">${((kpi.costPerUnit)!0)?string("0.00")}</div>
    </div>
</div>

<h2>每日出货 / 售后</h2>
<p class="legend">
    <span><i style="background:#4a7ebb"></i>出货台数</span>
    <span><i style="background:#b00000"></i>售后单数</span>
    <span class="hint">柱高按各自序列的最大值缩放，鼠标悬停可看具体数字</span>
</p>
<#if days?has_content>
    <div class="chart">
        <#list days as d>
            <div class="col" title="${d.date}：出货 ${d.shipped} 台 / 售后 ${d.claims} 单">
                <div class="bar ship" style="height:${d.shipPct}%"></div>
                <div class="bar claim" style="height:${d.claimPct}%"></div>
            </div>
        </#list>
    </div>
    <div class="xaxis">
        <#list days as d>
            <div class="tick"><#if d_index == 0 || !d_has_next || d_index % 10 == 5>${d.date}</#if></div>
        </#list>
    </div>
<#else>
    <p class="hint">还没有数据。</p>
</#if>

<h2>售后成本按责任方（近 30 天）</h2>
<#if resp?has_content>
    <div class="hbars">
        <#list resp as r>
            <div class="hbar">
                <div class="name"><@respLabel (r.responsibility)!''/></div>
                <div class="track"><div class="fill" style="width:${r.pct}%"></div></div>
                <div class="value">${((r.cost)!0)?string("0.00")}（${(r.claimCount)!0} 单）</div>
            </div>
        </#list>
    </div>
<#else>
    <p class="hint">统计期内没有售后单。</p>
</#if>

<h2>最近报工</h2>
<#if reports?has_content>
    <table>
        <tr><th>时间</th><th>工单</th><th class="num">工序</th><th>作业员</th>
            <th class="num">工时(h)</th><th class="num">良品</th><th class="num">不良</th><th>SN</th></tr>
        <#list reports as r>
            <tr>
                <td><#if r.reportDate??>${r.reportDate?string("MM-dd HH:mm")}</#if></td>
                <td><a href="/mfg/control/workOrder?workOrderId=${r.workOrderId}">${r.workOrderId}</a></td>
                <td class="num">${(r.opSeq)?string("0")}</td>
                <td>${r.operatorName!}</td>
                <td class="num"><#if r.workHours??>${r.workHours?string("0.##")}</#if></td>
                <td class="num">${(r.qtyOk)?string("0")}</td>
                <td class="num">${(r.qtyNg)?string("0")}</td>
                <td><#if r.sn??><a href="/mfg/control/sn?sn=${r.sn}">${r.sn}</a></#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有报工数据。</p>
</#if>

<script src="/mfg/js/dashboardRefresh.js"></script>

