<#ftl encoding="UTF-8">
<#--
  After sales quality report: return / exchange / repair rates, what they cost, and the breakdowns
  that tell where the money goes.
  Data: quality.* (see servicedef/services.xml#mfgQualityReport)
-->
<#assign q = (quality)!{}>
<#assign products = (q.products)![]>
<#assign responsibilities = (q.responsibilities)![]>
<#assign lots = (q.lots)![]>
<#assign ages = (q.ages)![]>
<#assign inspections = (q.inspections)![]>
<#macro money value>${(value!0)?string("0.00")}</#macro>
<#macro respLabel code>
    <#if (code!'') == 'HOUSE'>自制
    <#elseif (code!'') == 'SMT'>SMT 外协
    <#elseif (code!'') == 'SUPPLIER'>来料
    <#elseif (code!'') == 'DESIGN'>设计
    <#else>-</#if>
</#macro>
<#macro inspectLabel code>
    <#if (code!'') == 'QUALITY'>质量问题
    <#elseif (code!'') == 'NO_FAULT'>未复现
    <#elseif (code!'') == 'HUMAN'>人为损坏
    <#elseif (code!'') == 'UNKNOWN'>待判定
    <#else>${code!}</#if>
</#macro>

<h1>售后质量指标</h1>

<form method="get" action="/mfg/control/quality">
    从 <input type="text" name="fromDate" size="10" placeholder="yyyy-MM-dd"
        value="<#if q.fromDate??>${q.fromDate?string("yyyy-MM-dd")}</#if>"/>
    到 <input type="text" name="toDate" size="10" placeholder="yyyy-MM-dd"
        value="<#if q.toDate??>${q.toDate?string("yyyy-MM-dd")}</#if>"/>
    <input type="submit" value="查询"/>
    <span class="hint">默认最近 90 天；出货台数按出货单统计（换货出货单不计入销售额）。</span>
</form>

<h2>总览</h2>
<p>
    <span class="kpi"><span class="label">出货台数</span><br/><span class="value">${(q.shippedUnits)!0}</span></span>
    <span class="kpi"><span class="label">售后单数</span><br/><span class="value">${(q.claimCount)!0}</span></span>
    <span class="kpi"><span class="label">退货率</span><br/><span class="value">${(q.returnRate)!'-'}%</span></span>
    <span class="kpi"><span class="label">换货率</span><br/><span class="value">${(q.exchangeRate)!'-'}%</span></span>
    <span class="kpi"><span class="label">返修率</span><br/><span class="value">${(q.repairRate)!'-'}%</span></span>
    <span class="kpi"><span class="label">30 天内返修率</span><br/><span class="value">${(q.repairRate30)!'-'}%</span></span>
    <span class="kpi"><span class="label">售后成本率</span><br/><span class="value">${(q.costRate)!'-'}%</span></span>
</p>

<#if (q.overRepairLimit!'N') == 'Y'>
    <p class="error">⚠ 30 天内返修率超过红线 ${(q.repairRateLimit)!0}%：建议停线排查最近批次。</p>
</#if>
<#if (q.overCostLimit!'N') == 'Y'>
    <p class="error">⚠ 售后成本率超过红线 ${(q.costRateLimit)!0}%：建议复盘设计与定价。</p>
</#if>
<#if (q.repeatedSnCount!0) &gt; 0>
    <p class="warn">有 ${(q.repeatedSnCount)!0} 台 SN 在统计期内被售后两次以上，需要根因分析。</p>
</#if>

<h2>钱花在哪</h2>
<table>
    <tr><th>退款金额</th><td class="num"><@money (q.refundTotal)!0/></td>
        <th>我方承担运费</th><td class="num"><@money (q.freightTotal)!0/></td></tr>
    <tr><th>返修工时</th><td class="num">${((q.hoursTotal)!0)?string("0.##")} 小时</td>
        <th>售后总成本</th><td class="num"><@money (q.costTotal)!0/></td></tr>
    <tr><th>单台售后成本</th><td class="num"><@money (q.costPerUnit)!0/></td>
        <th>销售额（按型号售价估算）</th><td class="num"><@money (q.salesTotal)!0/></td></tr>
</table>
<p class="hint">成本 = 退款金额 + 我方承担运费（每单 ${(q.freightPerClaim)!0} 元）+ 返修工时 × ${(q.hourlyRate)!0} 元/小时。
    销售额 = 出货台数 × 型号售价，型号未填售价时为 0（成本率显示 “-”）。</p>

<h2>按型号</h2>
<#if products?has_content>
    <table>
        <tr><th>型号</th><th class="num">出货台数</th><th class="num">售后单</th><th>售后率</th>
            <th class="num">退款</th><th class="num">返修工时</th><th class="num">售后成本</th>
            <th class="num">销售额</th><th>成本率</th></tr>
        <#list products as p>
            <tr>
                <td><a href="/mfg/control/products?productCode=${p.productCode}">${p.productCode!}</a></td>
                <td class="num">${(p.shippedUnits)!0}</td>
                <td class="num">${(p.claimCount)!0}</td>
                <td>${(p.claimRate)!'-'}%</td>
                <td class="num"><@money (p.refundTotal)!0/></td>
                <td class="num">${((p.repairHours)!0)?string("0.##")}</td>
                <td class="num"><@money (p.cost)!0/></td>
                <td class="num"><@money (p.sales)!0/></td>
                <td>${(p.costRate)!'-'}%</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">统计期内没有出货。</p>
</#if>

<h2>按责任方</h2>
<#if responsibilities?has_content>
    <table>
        <tr><th>责任方</th><th class="num">售后单</th><th class="num">退款</th>
            <th class="num">返修工时</th><th class="num">成本</th></tr>
        <#list responsibilities as r>
            <tr>
                <td><@respLabel (r.productCode)!''/></td>
                <td class="num">${(r.claimCount)!0}</td>
                <td class="num"><@money (r.refundTotal)!0/></td>
                <td class="num">${((r.repairHours)!0)?string("0.##")}</td>
                <td class="num"><@money (r.cost)!0/></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">统计期内没有售后单。</p>
</#if>

<h2>按批次（集中度）</h2>
<#if lots?has_content>
    <table>
        <tr><th>批次</th><th>料号</th><th>供应商</th><th>供应商批号</th>
            <th class="num">相关售后单</th><th>判定</th></tr>
        <#list lots as l>
            <tr>
                <td><a href="/mfg/control/materials?lotId=${l.lotId}">${l.lotId!}</a></td>
                <td>${l.componentCode!}</td>
                <td>${l.supplierName!}</td>
                <td>${l.supplierLot!}</td>
                <td class="num">${(l.claimCount)!0}</td>
                <td><#if (l.suspicious!'N') == 'Y'><span class="error">高于均值 ${(q.lotFactor)!0} 倍</span>
                    <#else><span class="hint">正常</span></#if></td>
            </tr>
        </#list>
    </table>
    <p class="hint">按售后单所属工单的发料批次折算（SMT 不提供上料记录，属推定口径）。</p>
<#else>
    <p class="hint">统计期内没有可归批次的售后单。</p>
</#if>

<h2>失效时间分布</h2>
<#if ages?has_content>
    <table>
        <tr><th>出货后天数</th><th class="num">售后单</th></tr>
        <#list ages as a>
            <tr><td>${a.bucket!}</td><td class="num">${(a.claimCount)!0}</td></tr>
        </#list>
    </table>
    <p class="hint">集中在 0-30 天多为装配/来料问题；180 天以后增多更像设计余量或使用环境问题。</p>
<#else>
    <p class="hint">没有可计算的天数（缺出货日）。</p>
</#if>

<h2>检测结论</h2>
<#if inspections?has_content>
    <table>
        <tr><th>结论</th><th class="num">售后单</th></tr>
        <#list inspections as i>
            <tr><td><@inspectLabel (i.inspectResult)!''/></td><td class="num">${(i.claimCount)!0}</td></tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有填写检测结论。</p>
</#if>

