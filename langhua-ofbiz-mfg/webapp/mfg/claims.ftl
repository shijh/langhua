<#ftl encoding="UTF-8">
<#--
  Warranty claims (after sales). Warranty status is derived from the delivery date and the product
  warranty months, so the operator immediately sees whether the repair is covered.
  Data: claimList.claims, claimList.claimCount, claimList.openCount, claimDetail.*
-->
<#assign cl = (claimList)!{}>
<#assign claims = (cl.claims)![]>
<#assign det = (claimDetail)!{}>
<#assign claim = (det.claim)!{}>
<#assign snRecord = (det.snRecord)!{}>
<#assign deliveryInfo = (det.delivery)!{}>
<#assign exchangeDelivery = (det.exchangeDelivery)!{}>
<#assign exchangeUnits = (det.exchangeUnits)![]>
<#assign repairOrder = (det.repairOrder)!{}>
<#assign replacements = (det.replacements)![]>
<#assign errList = (errorMessageList)![]>
<#macro typeLabel t>
    <#if (t!'') == 'EXCH'>换货
    <#elseif (t!'') == 'REPAIR'>维修
    <#elseif (t!'') == 'REFUND_ONLY'>仅退款
    <#elseif (t!'') == 'RTN'>退货退款
    <#else>-</#if>
</#macro>
<#macro inspectLabel r>
    <#if (r!'') == 'QUALITY'>质量问题
    <#elseif (r!'') == 'NO_FAULT'>未复现
    <#elseif (r!'') == 'HUMAN'>人为损坏
    <#elseif (r!'') == 'UNKNOWN'>待判定
    <#else>-</#if>
</#macro>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>售后单 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; vertical-align: top; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .ok { color: #1a7f37; font-weight: bold; }
        .warn { color: #9a6700; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
        .big { font-size: 1.3em; font-weight: bold; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/delivery">出货单</a>
    <a href="/mfg/control/vendors">外协厂家</a>
    <a href="/mfg/control/claims"><b>售后单</b></a>
    <a href="/mfg/control/buyers">买家名单</a>
    <a href="/mfg/control/quality">质量指标</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>售后单（保修）</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p>
    共 ${(cl.claimCount)!0} 单，其中未结 <span class="big warn">${(cl.openCount)!0}</span> 单；
    7 天无理由窗口内 <span class="big">${(cl.sevenDayCount)!0}</span> 单，
    24 小时内到期 <span class="big error">${(cl.expiringCount)!0}</span> 单。
    <span class="hint">保修到期日 = 出货日 + 产品型号保修月数（默认 12 个月）；7 天无理由 / 15 天售后从买家签收日起算。</span>
</p>

<fieldset>
    <legend>登记售后单</legend>
    <form method="post" action="/mfg/control/claimCreate">
        <p>
            SN <input type="text" name="sn" size="18" required="required" placeholder="ITMFG-261021-0001"/>
            类型
            <select name="claimType">
                <option value="RTN">退货退款</option>
                <option value="EXCH">换货</option>
                <option value="REPAIR">维修</option>
                <option value="REFUND_ONLY">仅退款</option>
            </select>
            收件日期 <input type="text" name="receivedDate" size="10" placeholder="yyyy-MM-dd"/>
            买家签收日 <input type="text" name="signedDate" size="10" placeholder="yyyy-MM-dd"/>
        </p>
        <p>
            淘宝订单号 <input type="text" name="tradeNo" size="22" placeholder="如 3899..."/>
            买家昵称 <input type="text" name="buyerNick" size="16" placeholder="可选"/>
            责任预判
            <select name="responsibility">
                <option value="">（待定）</option>
                <option value="HOUSE">自制</option>
                <option value="SMT">SMT 外协</option>
                <option value="SUPPLIER">来料</option>
                <option value="DESIGN">设计</option>
            </select>
        </p>
        <p>
            问题描述 <input type="text" name="issueDesc" size="70" required="required" placeholder="客户反馈：无法开机 / 蓝牙断连…"/>
        </p>
        <p>
            备注 <input type="text" name="note" size="40" placeholder="可选"/>
            <input type="submit" value="登记"/>
        </p>
    </form>
    <p class="hint">登记的 SN 必须是已存在的主板；客户与出货日会自动从出货单带出。
        买家签收日留空时按出货日计算 7 天无理由与 15 天售后窗口。</p>
</fieldset>

<h2>售后单列表</h2>
<form method="get" action="/mfg/control/claims">
     SN：<input type="text" name="sn" size="18" value="${(parameters.sn)!''}"/>
     类型：
    <select name="claimType">
        <option value="">（全部）</option>
        <option value="RTN"<#if (parameters.claimType!'') == 'RTN'> selected="selected"</#if>>退货退款</option>
        <option value="EXCH"<#if (parameters.claimType!'') == 'EXCH'> selected="selected"</#if>>换货</option>
        <option value="REPAIR"<#if (parameters.claimType!'') == 'REPAIR'> selected="selected"</#if>>维修</option>
        <option value="REFUND_ONLY"<#if (parameters.claimType!'') == 'REFUND_ONLY'> selected="selected"</#if>>仅退款</option>
    </select>
     状态：
    <select name="statusId">
        <option value="">（全部）</option>
        <option value="MFG_RMA_OPEN"<#if (parameters.statusId!'') == 'MFG_RMA_OPEN'> selected="selected"</#if>>未结</option>
        <option value="MFG_RMA_REPAIRED"<#if (parameters.statusId!'') == 'MFG_RMA_REPAIRED'> selected="selected"</#if>>已修好</option>
        <option value="MFG_RMA_REPLACED"<#if (parameters.statusId!'') == 'MFG_RMA_REPLACED'> selected="selected"</#if>>已换机</option>
        <option value="MFG_RMA_CLOSED"<#if (parameters.statusId!'') == 'MFG_RMA_CLOSED'> selected="selected"</#if>>已结案</option>
    </select>
    <input type="submit" value="筛选"/>
</form>

<#if claims?has_content>
    <table>
        <tr><th>单号</th><th>类型</th><th>订单号</th><th>收件日</th><th>SN</th><th>签收日</th>
            <th>7 天无理由</th><th>15 天售后</th><th>保修</th><th>问题</th><th>状态</th><th>责任</th><th class="num">工时</th></tr>
        <#list claims as c>
            <#assign sevenLeft = (c.sevenDayHoursLeft)!0>
            <#assign saleLeft = (c.afterSaleHoursLeft)!0>
            <tr>
                <td><a href="/mfg/control/claims?claimId=${c.claimId}">${c.claimId!}</a></td>
                <td><@typeLabel (c.claimType)!''/></td>
                <td>${c.tradeNo!}</td>
                <td><#if c.receivedDate??>${c.receivedDate?string("yyyy-MM-dd")}</#if></td>
                <td><a href="/mfg/control/sn?sn=${c.sn}">${c.sn!}</a></td>
                <td><#if c.signedDate??>${c.signedDate?string("yyyy-MM-dd")}<#else>-</#if></td>
                <td>
                    <#if (sevenLeft > 0)>
                        <#if (sevenLeft lte 24)><span class="error">剩 ${sevenLeft} 小时</span>
                        <#else><span class="ok">剩 ${(sevenLeft / 24)?floor} 天</span></#if>
                    <#else><span class="hint">已过期</span></#if>
                </td>
                <td>
                    <#if (saleLeft > 0)>
                        <#if (saleLeft lte 24)><span class="error">剩 ${saleLeft} 小时</span>
                        <#else><span class="ok">剩 ${(saleLeft / 24)?floor} 天</span></#if>
                    <#else><span class="hint">已过期</span></#if>
                </td>
                <td><#if (c.inWarranty!'N') == 'Y'><span class="ok">保内</span><#else><span class="error">保外</span></#if></td>
                <td>${c.issueDesc!}</td>
                <td>
                    <#if (c.statusId!'') == 'MFG_RMA_CLOSED'>已结案
                    <#elseif (c.statusId!'') == 'MFG_RMA_REPAIRED'>已修好
                    <#elseif (c.statusId!'') == 'MFG_RMA_REPLACED'>已换机
                    <#else><span class="warn">未结</span></#if>
                </td>
                <td>${c.responsibility!}</td>
                <td class="num"><#if c.repairHours??>${c.repairHours?string("0.##")}</#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">没有售后单。</p>
</#if>

<#if (claim.claimId)??>
    <h2>售后单详情 ${claim.claimId}</h2>
    <#if (claim.buyerRiskLevel)??>
        <#if (claim.buyerRiskLevel!'') == 'BLOCK'>
            <p class="error">⚠ 该买家在风险名单中（人工复核）：发货 / 换货前请复核收货信息并保留开箱视频等证据。
                质量问题与 7 天无理由仍须照常处理。<a href="/mfg/control/buyers">查看买家名单</a></p>
        <#else>
            <p class="warn">⚠ 该买家在风险名单中（观察）：注意留证。<a href="/mfg/control/buyers">查看买家名单</a></p>
        </#if>
    </#if>
    <table>
        <tr><th>SN</th>
            <td><a href="/mfg/control/sn?sn=${claim.sn}">${claim.sn!}</a>
                <#if (snRecord.statusId)??>（${snRecord.statusId}）</#if></td></tr>
        <tr><th>工单</th><td><a href="/mfg/control/workOrder?workOrderId=${claim.workOrderId}">${claim.workOrderId!}</a></td></tr>
        <tr><th>类型 / 状态</th>
            <td><@typeLabel (claim.claimType)!''/>
                <#if (claim.statusId!'') == 'MFG_RMA_CLOSED'>已结案
                <#elseif (claim.statusId!'') == 'MFG_RMA_REPAIRED'>已修好
                <#elseif (claim.statusId!'') == 'MFG_RMA_REPLACED'>已换机
                <#else><span class="warn">未结</span></#if></td></tr>
        <tr><th>淘宝订单 / 买家</th><td>${claim.tradeNo!'-'} / ${claim.buyerNick!'-'}</td></tr>
        <tr><th>客户</th><td>${claim.customerName!}
            <#if (claim.deliveryId)??>（出货单 <a href="/mfg/control/delivery?deliveryId=${claim.deliveryId}">${claim.deliveryId}</a>）</#if></td></tr>
        <tr><th>出货日 / 保修到期</th>
            <td><#if claim.deliveryDate??>${claim.deliveryDate?string("yyyy-MM-dd")}<#else>-</#if>
                / <#if claim.warrantyEndDate??>${claim.warrantyEndDate?string("yyyy-MM-dd")}<#else>-</#if>
                <#if (claim.inWarranty!'N') == 'Y'><span class="ok">保内</span><#else><span class="error">保外</span></#if></td></tr>
        <tr><th>买家签收日</th>
            <td><#if claim.signedDate??>${claim.signedDate?string("yyyy-MM-dd")}<#else>-</#if></td></tr>
        <tr><th>7 天无理由截止</th>
            <td><#if claim.sevenDayDeadline??>${claim.sevenDayDeadline?string("yyyy-MM-dd HH:mm")}<#else>-</#if>
                <#assign sevenLeft = (claim.sevenDayHoursLeft)!0>
                <#if (sevenLeft > 0)>
                    <#if (sevenLeft lte 24)><span class="error">剩 ${sevenLeft} 小时</span>
                    <#else><span class="ok">剩 ${(sevenLeft / 24)?floor} 天</span></#if>
                <#else><span class="hint">已过期</span></#if></td></tr>
        <tr><th>15 天售后截止</th>
            <td><#if claim.afterSaleDeadline??>${claim.afterSaleDeadline?string("yyyy-MM-dd HH:mm")}<#else>-</#if>
                <#assign saleLeft = (claim.afterSaleHoursLeft)!0>
                <#if (saleLeft > 0)>
                    <#if (saleLeft lte 24)><span class="error">剩 ${saleLeft} 小时</span>
                    <#else><span class="ok">剩 ${(saleLeft / 24)?floor} 天</span></#if>
                <#else><span class="hint">已过期</span></#if></td></tr>
        <tr><th>退回快递 / 回库日</th>
            <td>${claim.returnTrackingNo!'-'} / <#if claim.receivedBackDate??>${claim.receivedBackDate?string("yyyy-MM-dd")}<#else>-</#if></td></tr>
        <tr><th>检测结论</th><td><@inspectLabel (claim.inspectResult)!''/></td></tr>
        <tr><th>退款 / 扣款</th>
            <td><#if claim.refundAmount??>${claim.refundAmount?string("0.00")}<#else>0.00</#if>
                / <#if claim.deductAmount??>${claim.deductAmount?string("0.00")}<#else>0.00</#if></td></tr>
        <tr><th>运费承担</th>
            <td><#if (claim.freightPayer!'') == 'SELLER'>我方<span class="warn">（质量问题，来回运费由卖家承担）</span>
                <#elseif (claim.freightPayer!'') == 'BUYER'>买家<#else>-</#if></td></tr>
        <tr><th>SMT / 供应商索赔</th><td>${claim.vendorClaimStatus!'-'}</td></tr>
        <tr><th>换货出货单</th>
            <td><#if (claim.exchangeDeliveryId)??><a href="/mfg/control/delivery?deliveryId=${claim.exchangeDeliveryId}">${claim.exchangeDeliveryId}</a>
                <#else>-</#if></td></tr>
        <tr><th>换出新机 SN</th>
            <td><#if exchangeUnits?has_content><#list exchangeUnits as u><a href="/mfg/control/sn?sn=${u.sn}">${u.sn}</a><#if u_has_next>、</#if></#list><#else>-</#if></td></tr>
        <tr><th>返修工单</th>
            <td><#if (claim.repairWorkOrderId)??><a href="/mfg/control/workOrder?workOrderId=${claim.repairWorkOrderId}">${claim.repairWorkOrderId}</a>
                <#else>-</#if></td></tr>
        <tr><th>问题</th><td>${claim.issueDesc!}</td></tr>
        <tr><th>该工单不良记录数</th><td>${(det.defectCount)!0}</td></tr>
    </table>

    <h3 style="font-size:1em;margin-top:1.2em;">该板换件记录</h3>
    <#if replacements?has_content>
        <table>
            <tr><th>日期</th><th>料号</th><th>原批次</th><th>新批次</th><th class="num">数量</th><th>原因</th></tr>
            <#list replacements as r>
                <tr>
                    <td><#if r.replacedDate??>${r.replacedDate?string("yyyy-MM-dd")}</#if></td>
                    <td>${r.componentCode!}</td>
                    <td>${r.oldLotId!}</td>
                    <td>${r.newLotId!}</td>
                    <td class="num">${((r.qty)!0)?string("0.##")}</td>
                    <td>${r.reason!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">没有换件记录。</p>
    </#if>

    <fieldset>
        <legend>处理售后单</legend>
        <form method="post" action="/mfg/control/claimUpdate">
            <input type="hidden" name="claimId" value="${claim.claimId}"/>
            <p>
                状态
                <select name="statusId">
                    <option value="MFG_RMA_OPEN"<#if (claim.statusId!'') == 'MFG_RMA_OPEN'> selected="selected"</#if>>未结</option>
                    <option value="MFG_RMA_REPAIRED"<#if (claim.statusId!'') == 'MFG_RMA_REPAIRED'> selected="selected"</#if>>已修好</option>
                    <option value="MFG_RMA_REPLACED"<#if (claim.statusId!'') == 'MFG_RMA_REPLACED'> selected="selected"</#if>>已换机</option>
                    <option value="MFG_RMA_CLOSED"<#if (claim.statusId!'') == 'MFG_RMA_CLOSED'> selected="selected"</#if>>已结案</option>
                </select>
                类型
                <select name="claimType">
                    <option value="RTN"<#if (claim.claimType!'') == 'RTN'> selected="selected"</#if>>退货退款</option>
                    <option value="EXCH"<#if (claim.claimType!'') == 'EXCH'> selected="selected"</#if>>换货</option>
                    <option value="REPAIR"<#if (claim.claimType!'') == 'REPAIR'> selected="selected"</#if>>维修</option>
                    <option value="REFUND_ONLY"<#if (claim.claimType!'') == 'REFUND_ONLY'> selected="selected"</#if>>仅退款</option>
                </select>
                责任
                <select name="responsibility">
                    <option value="HOUSE"<#if (claim.responsibility!'') == 'HOUSE'> selected="selected"</#if>>自制</option>
                    <option value="SMT"<#if (claim.responsibility!'') == 'SMT'> selected="selected"</#if>>SMT 外协</option>
                    <option value="SUPPLIER"<#if (claim.responsibility!'') == 'SUPPLIER'> selected="selected"</#if>>来料</option>
                    <option value="DESIGN"<#if (claim.responsibility!'') == 'DESIGN'> selected="selected"</#if>>设计</option>
                </select>
                返修工时 <input type="text" name="repairHours" size="5" value="${(claim.repairHours)!0}"/>
            </p>
            <p>
                检测结论
                <select name="inspectResult">
                    <option value=""<#if !(claim.inspectResult)??> selected="selected"</#if>>（未判定）</option>
                    <option value="QUALITY"<#if (claim.inspectResult!'') == 'QUALITY'> selected="selected"</#if>>质量问题</option>
                    <option value="NO_FAULT"<#if (claim.inspectResult!'') == 'NO_FAULT'> selected="selected"</#if>>未复现</option>
                    <option value="HUMAN"<#if (claim.inspectResult!'') == 'HUMAN'> selected="selected"</#if>>人为损坏</option>
                    <option value="UNKNOWN"<#if (claim.inspectResult!'') == 'UNKNOWN'> selected="selected"</#if>>待判定</option>
                </select>
                运费
                <select name="freightPayer">
                    <option value="BUYER"<#if (claim.freightPayer!'') == 'BUYER'> selected="selected"</#if>>买家承担</option>
                    <option value="SELLER"<#if (claim.freightPayer!'') == 'SELLER'> selected="selected"</#if>>我方承担</option>
                </select>
                退款 <input type="text" name="refundAmount" size="7" value="${(claim.refundAmount)!0}"/>
                扣款 <input type="text" name="deductAmount" size="6" value="${(claim.deductAmount)!0}"/>
            </p>
            <p>
                退回快递单号 <input type="text" name="returnTrackingNo" size="18" value="${claim.returnTrackingNo!}"/>
                回库日期 <input type="text" name="receivedBackDate" size="10" placeholder="yyyy-MM-dd"
                    value="<#if claim.receivedBackDate??>${claim.receivedBackDate?string("yyyy-MM-dd")}</#if>"/>
                索赔状态
                <select name="vendorClaimStatus">
                    <option value="NONE"<#if (claim.vendorClaimStatus!'NONE') == 'NONE'> selected="selected"</#if>>无需索赔</option>
                    <option value="SENT"<#if (claim.vendorClaimStatus!'') == 'SENT'> selected="selected"</#if>>已向厂家索赔</option>
                    <option value="RECOVERED"<#if (claim.vendorClaimStatus!'') == 'RECOVERED'> selected="selected"</#if>>已收回</option>
                </select>
            </p>
            <p>
                买家签收日修正 <input type="text" name="signedDate" size="10" placeholder="yyyy-MM-dd"
                    value="<#if claim.signedDate??>${claim.signedDate?string("yyyy-MM-dd")}</#if>"/>
                备注 <input type="text" name="note" size="28" value="${claim.note!}"/>
                <input type="submit" value="保存"/>
            </p>
        </form>
        <p class="hint">需要记录换件时，到 <a href="/mfg/control/sn?sn=${claim.sn}">SN 履历</a> 页登记（会精确到单板）。</p>

        <form method="post" action="/mfg/control/claimExchange" style="display:inline-block;margin-right:2em;">
            <input type="hidden" name="claimId" value="${claim.claimId}"/>
            换货：新 SN（留空自动取同型号未出货的一台）
            <input type="text" name="newSn" size="18" placeholder="留空自动选"/>
            快递 <input type="text" name="carrier" size="6"/>
            单号 <input type="text" name="trackingNo" size="14"/>
            <input type="submit" value="出换货单"/>
        </form>
        <form method="post" action="/mfg/control/claimRepair" style="display:inline-block;">
            <input type="hidden" name="claimId" value="${claim.claimId}"/>
            维修：返修工单交期 <input type="text" name="dueDate" size="10" placeholder="yyyy-MM-dd"/>
            <input type="submit" value="开返修工单"/>
        </form>
        <p class="hint">换货会自动生成一张出货单并把新 SN 置为已出货；返修工单用于记录返工工时与换件。</p>
    </fieldset>
</#if>
</body>
</html>
