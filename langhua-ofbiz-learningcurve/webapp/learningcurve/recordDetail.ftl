<#ftl encoding="UTF-8">
<#--
  One stored estimate: header, history batches, learning curve chart.
  Data: chart.estimate (map), chart.batches (list), chart.points (list), chart.maxX / chart.maxY
-->
<#assign ch = (chart)!{}>
<#assign e = (ch.estimate)!{}>
<#assign batches = (ch.batches)![]>
<#assign points = (ch.points)![]>
<#assign h = (hours)!{}>
<#assign maxX = (ch.maxX)!1>
<#assign maxY = (ch.maxY)!1>
<#if maxX <= 0><#assign maxX = 1></#if>
<#if maxY <= 0><#assign maxY = 1></#if>
<#assign plotW = 540>
<#assign plotH = 200>
<#assign x0 = 60>
<#assign y0 = 250>

<#if !(e.estimateId??)>
    <h1>记录不存在</h1>
    <p><a href="/learningcurve/control/records">← 返回记录列表</a></p>
<#else>
    <h1>${e.productCode!} <#if (e.methodType?int) == 2>（Crawford）<#else>（Wright）</#if></h1>
    <p>
        <a href="/learningcurve/control/records">← 返回记录列表</a> |
        <a href="/learningcurve/control/exportCsv?estimateId=${e.estimateId}">下载 CSV</a> |
        <a href="/learningcurve/control/main">新建估算</a>
    </p>

    <h2>估算结果</h2>
    <table>
        <tr><th>时间</th><td><#if e.createdStamp??>${e.createdStamp?string("yyyy-MM-dd HH:mm")}</#if></td></tr>
        <tr><th>口径</th><td>${e.measureType!}</td></tr>
        <tr><th>待估算单位数</th><td>${(e.newUnitNumber)?string("0.####")}</td></tr>
        <tr><th>估算总额</th><td>${(e.estimateTotal)?string("0.####")}</td></tr>
        <tr><th>估算单位值</th><td>${(e.estimateUnit)?string("0.####")}</td></tr>
        <tr><th>斜率</th><td>${(e.slope)?string("0.######")}</td></tr>
        <tr><th>截距</th><td>${(e.intercept)?string("0.######")}</td></tr>
        <tr><th>实际 R&sup2;</th><td>${(e.leastSquareActual)?string("0.######")}</td></tr>
        <tr><th>参与拟合批次 / 剔除批次</th><td>${(e.qualifiedRows)?string("0")} / ${(e.filteredRows)?string("0")}</td></tr>
    </table>

    <h2>趋势图（单位值 vs 累计数量）</h2>
    <p class="legend">
    <span><i class="dot" style="background:#f14c4c"></i>实际</span>
    <span><svg width="18" height="10"><line x1="0" y1="5" x2="18" y2="5" stroke="#4daafc" stroke-width="2"/></svg> 拟合</span>
    </p>
    <svg width="620" height="280" xmlns="http://www.w3.org/2000/svg">
        <line x1="${x0}" y1="${y0}" x2="${x0 + plotW}" y2="${y0}" stroke="#4b4b4b"/>
        <line x1="${x0}" y1="${y0}" x2="${x0}" y2="${y0 - plotH}" stroke="#4b4b4b"/>
        <text x="${x0 - 8}" y="${y0}" text-anchor="end" font-size="11">0</text>
        <text x="${x0 - 8}" y="${y0 - plotH + 4}" text-anchor="end" font-size="11">${(maxY)?string("0.##")}</text>
        <text x="${x0 + plotW}" y="${y0 + 16}" text-anchor="end" font-size="11">${(maxX)?string("0.##")} 单位</text>
        <#if points?has_content>
            <polyline fill="none" stroke="#4daafc" stroke-width="2" points="<#list points as pt>${(x0 + (pt.units / maxX) * plotW)?string("0.#")},${(y0 - (pt.predicted / maxY) * plotH)?string("0.#")}<#if pt_has_next> </#if></#list>"/>
            <#list points as pt>
                <circle cx="${(x0 + (pt.units / maxX) * plotW)?string("0.#")}" cy="${(y0 - (pt.actual / maxY) * plotH)?string("0.#")}" r="4" fill="#f14c4c"/>
            </#list>
        </#if>
    </svg>

    <h2>历史批次</h2>
    <table>
        <tr><th>批次</th><th class="num">数量</th><th class="num">总额</th><th class="num">单位值</th></tr>
        <#list batches as b>
            <tr>
                <td>${(b.batchSeq)?string("0")}</td>
                <td class="num">${(b.lotSize)?string("0.####")}</td>
                <td class="num">${(b.lotValue)?string("0.####")}</td>
                <td class="num"><#if b.lotSize != 0>${(b.lotValue / b.lotSize)?string("0.####")}</#if></td>
            </tr>
        </#list>
    </table>

    <h2>回填成 MES 型号的标准工时</h2>
    <#if (parameters.applied!'') == 'Y'>
        <p class="ok">已回填：型号的标准工时已按这条曲线更新，旧值记在下面的台账里。</p>
    </#if>
    <#if ((h.proposedUnitHours)!0) gt 0>
        <p class="hint">曲线给出的不是「某一台」的工时，而是「下一批」的工时；这里按基准批量摊成单台，
            写进 MES 型号 <code>${h.productCode!}</code> 的标准工时（型号页会注明来源与基准）。</p>
        <table id="hoursPreview">
            <tr>
                <th>基准批量（台）</th>
                <th class="num">该批合计工时</th>
                <th class="num">单台工时（曲线）</th>
                <th class="num">MES 现用标准工时</th>
                <th class="num">差额</th>
                <th>MES 现用来源</th>
            </tr>
            <tr>
                <td>${(h.baseQty)?string("0.##")}</td>
                <td class="num">${(h.estimateTotal)?string("0.###")}</td>
                <td class="num">${(h.proposedUnitHours)?string("0.###")}</td>
                <td class="num"><#if (h.existsInMes!'') == 'Y'>${(h.currentHours)?string("0.###")}<#else>—</#if></td>
                <td class="num"><#if (h.existsInMes!'') == 'Y'><#if (((h.deltaHours)!0)?abs) lt 0.0005>无变化<#else><#if ((h.deltaHours)!0) gt 0>+</#if>${(h.deltaHours)?string("0.###")}（<#if ((h.deltaPercent)!0) gt 0>+</#if>${(h.deltaPercent)?string("0.#")}%）</#if><#else>—</#if></td>
                <td data-source="${(h.currentSource)!''}"><#if (h.currentSource!'') == 'LEARNING_CURVE'>学习曲线回填（基准 ${((h.currentBaseQty)!0)?string("0.##")} 台，估算 ${(h.currentRef)!''}）<#elseif (h.currentSource!'') == 'MANUAL'>手工填写<#else>—</#if></td>
            </tr>
        </table>
        <#if (h.canApply!'') == 'Y'>
            <form method="post" action="/learningcurve/control/standardHoursApply">
                <p>
                    <input type="hidden" name="estimateId" value="${e.estimateId}"/>
                    基准批量 <input type="text" name="baseQty" size="6" value="${(h.baseQty)?string("0.##")}"/>
                    <input type="submit" value="回填到型号标准工时"/>
                    <span class="hint">回填会覆盖型号上现有的标准工时，旧值记在下面的台账里；成本核算立刻按新工时算。</span>
                </p>
            </form>
        <#else>
            <p class="hint">${(h.message)!'这条估算暂时不能回填'}</p>
        </#if>
    <#else>
        <p class="hint">${(h.message)!'这条估算暂时不能回填'}</p>
    </#if>
    <#if (h.applies)?has_content>
        <table id="hoursApplyTable">
            <tr><th>时间</th><th>估算</th><th class="num">基准批量</th><th class="num">原工时</th><th class="num">新工时</th><th>操作人</th></tr>
            <#list h.applies as row>
                <tr>
                    <td><#if row.createdStamp??>${row.createdStamp?string("yyyy-MM-dd HH:mm")}</#if></td>
                    <td><a href="/learningcurve/control/recordDetail?estimateId=${row.estimateId!}">${row.estimateId!}</a></td>
                    <td class="num">${((row.baseQty)!0)?string("0.##")}</td>
                    <td class="num">${((row.oldHours)!0)?string("0.###")}</td>
                    <td class="num">${((row.newHours)!0)?string("0.###")}</td>
                    <td>${row.appliedBy!''}</td>
                </tr>
            </#list>
        </table>
    </#if>

    <h2>原始输入</h2>
    <p><code>${e.historyData!}</code></p>
</#if>
