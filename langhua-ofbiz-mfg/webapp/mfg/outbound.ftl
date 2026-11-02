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
           data-outbound-note="${(outbound.note)!}" data-outbound-reason="${(outbound.cancelReason)!}"
           data-outbound-receiver="${(outbound.receiverName)!}"
           data-outbound-status="<#if outbound.issued>ISSUED<#elseif outbound.cancelled>CANCELLED<#elseif outbound.packed>PACKED<#else>DRAFT</#if>"
           data-outbound-packages="${(outbound.packageCount!0)?string("0.##")}"
           data-outbound-check-key="${outbound.checkKey!''}"
           data-outbound-issued-at="<#if outbound.issuedAt??>${outbound.issuedAt?string("yyyy-MM-dd HH:mm")}</#if>"
           data-outbound-issued-by="${(outbound.issuedBy)!}">
        <tr>
            <th>日期</th><th>BOM 版本</th><th class="num">行数</th><th class="num">合计</th>
            <th class="num">已出</th><th>状态</th><th>制作</th><th>领料人</th><th>包装</th><th>备注</th>
        </tr>
        <tr>
            <td><#if outbound.outboundDate??>${outbound.outboundDate?string("yyyy-MM-dd")}</#if></td>
            <td>${outbound.bomVersion!}</td>
            <td class="num">${(outbound.lineCount)!0}</td>
            <td class="num">${(outbound.totalQty!0)?string("0.##")}</td>
            <td class="num">${(outbound.issuedQty!0)?string("0.##")}</td>
            <td><#if outbound.issued><span class="ok">已出库</span><#elseif outbound.cancelled><span class="hint"<#if ((outbound.cancelReason)!'')?has_content> title="作废原因：${outbound.cancelReason}"</#if>>已作废</span><#elseif outbound.packed><span class="warn">已包装</span><#else><span class="warn">待出库</span></#if></td>
            <td>${outbound.operatorName!}</td>
            <td>${(outbound.receiverName)!}</td>
            <td><#if outbound.packed || outbound.issued>${(outbound.packageCount!0)?string("0.##")} 盒<#else><span class="hint">—</span></#if></td>
            <td>${(outbound.note)!}</td>
        </tr>
    </table>

    <#if parameters.saved??>
        <p class="ok">领料人已保存。</p>
    </#if>
    <#if parameters.packed??>
        <p class="ok">包装已登记：${parameters.packed}。把标签（下面「打印标签」）贴到盒子上。</p>
    </#if>

    <#-- 电子产品一般装塑料盒，盒子上贴一张带二维码的标签，手机扫一下就能对着明细核对（Step 247） -->
    <#if !outbound.issued && !outbound.cancelled>
        <form method="post" action="/mfg/control/outboundPack" class="inline">
            <input type="hidden" name="outboundId" value="${outbound.outboundId}"/>
            装 <input type="text" name="packageCount" size="3" value="<#if outbound.packed>${(outbound.packageCount!1)?string("0.##")}<#else>1</#if>"/> 盒
            包装说明 <input type="text" name="packingNote" size="20" value="${(outbound.packingNote)!}" placeholder="塑料盒 / 防静电袋"/>
            <input type="submit" value="<#if outbound.packed>改包装<#else>登记包装</#if>" class="secondary"/>
        </form>
        <span class="hint">可选的一步：料捡完装进塑料盒（电子产品防静电），登记后打印标签贴到盒子上；只有一件小东西也可以不包，直接出库。</span>
    </#if>
    <#if outbound.packed || outbound.issued>
        <p>
            <a href="/mfg/control/outboundLabel?outboundId=${outbound.outboundId?url}" target="_blank">打印标签（80×50mm）</a>
            ｜ <a href="/mfg/control/outboundCheck?outboundId=${outbound.outboundId?url}&amp;key=${outbound.checkKey!''}" target="_blank">核对页（扫码看到的就是它）</a>
            <#if outbound.packedDate??>｜ <span class="hint">包装：${(outbound.packageCount!0)?string("0.##")} 盒<#if ((outbound.packingNote)!'')?has_content>（${outbound.packingNote}）</#if>，${outbound.packedDate?string("yyyy-MM-dd HH:mm")}<#if ((outbound.packedByUserLoginId)!'')?has_content>，${outbound.packedByUserLoginId}</#if></span></#if>
        </p>
    </#if>

    <form method="post" action="/mfg/control/outboundReceiverSave" class="inline">
        <input type="hidden" name="outboundId" value="${outbound.outboundId}"/>
        领料人 <input type="text" name="receiverName" size="12" value="${(outbound.receiverName)!}" placeholder="谁把料拿走"/>
        <input type="submit" value="保存领料人" class="secondary"/>
    </form>
    <span class="hint">领料人就是来拿料的那个人，不是系统账号也能写；库管员是点「按这张单出库」的那个账号，记在库存流水的操作人上。</span>

    <#if outbound.issued && outbound.issuedAt??>
        <p class="hint" id="outboundHanded">出库：${outbound.issuedAt?string("yyyy-MM-dd HH:mm")}<#if ((outbound.issuedBy)!'')?has_content>，经办 ${outbound.issuedBy}</#if><#if (outbound.movementCount!0) gt 0>（${outbound.movementCount} 条库存流水）</#if>。</p>
    </#if>

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
        ｜ <a href="/mfg/control/outbounds">全部出库单</a>
        <#if outbound.issued>
            ｜ <a href="/mfg/control/stockMoves?search=${outbound.outboundId}">在库存流水里看这张单</a>
        <#elseif outbound.cancelled>
            ｜ <span class="hint">这张单已作废，从没动过货架，所以库存流水里没有它。</span>
        <#else>
            ｜ <span class="hint">这张单还没出库，库存流水里还没有它；按这张单出库之后，这里会出现「在库存流水里看这张单」。</span>
        </#if>
    </p>
</#if>
