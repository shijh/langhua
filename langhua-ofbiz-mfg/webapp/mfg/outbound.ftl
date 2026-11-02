<#ftl encoding="UTF-8">
<#assign outbound = (outboundResult.outbound)!{}>
<#assign errList = (errorMessageList)![]>
<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<#if !outbound?has_content>
    <h1>出库单</h1>
    <p class="error">没有这张出库单：单号可能写错了，或者这张单已经不在库里。</p>
    <p><a href="/mfg/control/dev">回到产品开发</a></p>
<#else>
    <h1>出库单 ${outbound.outboundId!''}</h1>

    <#if parameters.issuedQty??>
        <p class="ok">这张单已出库 <b>${parameters.issuedQty}</b> 件：库存按先进先出扣减，
            本项目在那些批次上的锁也放掉了同样数量。</p>
    <#elseif parameters.cancelled??>
        <p class="ok">这张单已作废（当作没开过，可以重新生成一张）。</p>
    </#if>

    <p class="hint">这张单是按研发 BOM 的那一版写出来的领料清单：<b>本次领</b>已经是差额
        （这一版要减去本项目之前已经领过的），已领够的料不会出现在单上。</p>

    <table id="outboundHead" data-outbound="${outbound.outboundId!''}"
           data-outbound-by="${outbound.operatorName!}"
           data-outbound-note="${(outbound.note)!}" data-outbound-reason="${(outbound.cancelReason)!}">
        <tr>
            <th>日期</th><th>BOM 版本</th><th class="num">行数</th><th class="num">合计</th>
            <th class="num">已出</th><th>状态</th><th>制作</th><th>备注</th>
        </tr>
        <tr>
            <td><#if outbound.outboundDate??>${outbound.outboundDate?string("yyyy-MM-dd")}</#if></td>
            <td>${outbound.bomVersion!}</td>
            <td class="num">${(outbound.lineCount)!0}</td>
            <td class="num">${(outbound.totalQty!0)?string("0.##")}</td>
            <td class="num">${(outbound.issuedQty!0)?string("0.##")}</td>
            <td><#if outbound.issued><span class="ok">已出库</span><#elseif outbound.cancelled><span class="hint"<#if ((outbound.cancelReason)!'')?has_content> title="作废原因：${outbound.cancelReason}"</#if>>已作废</span><#else><span class="warn">待出库</span></#if></td>
            <td>${outbound.operatorName!}</td>
            <td>${(outbound.note)!}</td>
        </tr>
    </table>

    <#if outbound.cancelled && ((outbound.cancelReason)!'')?has_content>
        <p class="hint">作废原因：${outbound.cancelReason}<#if outbound.cancelDate??>（${outbound.cancelDate?string("yyyy-MM-dd HH:mm")}<#if ((outbound.cancelByUserLoginId)!'')?has_content>，${outbound.cancelByUserLoginId}</#if>）</#if></p>
    </#if>

    <#if !outbound.issued && !outbound.cancelled>
        <form method="post" action="/mfg/control/outboundIssueHere" class="inline">
            <input type="hidden" name="outboundId" value="${outbound.outboundId}"/>
            <input type="submit" value="按这张单出库"/>
        </form>
        <form method="post" action="/mfg/control/outboundCancelHere" class="inline">
            <input type="hidden" name="outboundId" value="${outbound.outboundId}"/>
            <input type="submit" value="撤掉这张单" class="secondary"
                   onclick="return confirm('作废 ${outbound.outboundId}？库存一点不动，只是这张单不再用。');"/>
        </form>
        <span class="hint">只要有一个料号不够，整张单都不出库，会告诉你是哪个料号缺多少。</span>
    </#if>

    <h3>领料清单（${outbound.lines?size} 项）</h3>
    <table id="outboundLines">
        <tr>
            <th class="num">#</th><th>料号</th><th>名称</th><th class="num">这一版要</th>
            <th class="num">之前已出</th><th class="num">本次领</th><th class="num">这张单已出</th><th>位号</th>
        </tr>
        <#list outbound.lines as ln>
            <tr data-outbound-line="${ln.componentCode}">
                <td class="num">${ln_index + 1}</td>
                <td>${ln.componentCode}</td>
                <td>${ln.componentName!}</td>
                <td class="num">${(ln.neededQty!0)?string("0.##")}</td>
                <td class="num">${(ln.alreadyQty!0)?string("0.##")}</td>
                <td class="num">${(ln.qty!0)?string("0.##")}</td>
                <td class="num">${(ln.issuedQty!0)?string("0.##")}</td>
                <td>${ln.designators!}</td>
            </tr>
        </#list>
    </table>

    <p>
        <#if outbound.projectCode??>
            <a href="/mfg/control/dev?projectCode=${outbound.projectCode}#devoutbound">回到项目 ${outbound.projectCode} 的出库单一段</a>
        <#else>
            <a href="/mfg/control/dev">回到产品开发</a>
        </#if>
        <#if outbound.issued>
            ｜ <a href="/mfg/control/stockMoves?search=${outbound.outboundId}">在库存流水里看这张单</a>
        <#elseif outbound.cancelled>
            ｜ <span class="hint">这张单已作废，从没动过货架，所以库存流水里没有它。</span>
        <#else>
            ｜ <span class="hint">这张单还没出库，库存流水里还没有它；按这张单出库之后，这里会出现「在库存流水里看这张单」。</span>
        </#if>
    </p>
</#if>
