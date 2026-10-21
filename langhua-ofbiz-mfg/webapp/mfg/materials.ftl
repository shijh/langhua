<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Material lot ledger and backward trace.
  Data: lotResult.lots, trace.lots, trace.workOrders, trace.totalSnCount
-->
<#assign lr = (lotResult)!{}>
<#assign lots = (lr.lots)![]>
<#assign tr = (trace)!{}>
<#assign tracedLots = (tr.lots)![]>
<#assign tracedOrders = (tr.workOrders)![]>
<#assign errList = (errorMessageList)![]>

<h1>物料批次</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>登记批次（采购到货）</legend>
    <form method="post" action="/mfg/control/lotSave">
        <p>
            元件料号 <input type="text" name="componentCode" size="16" placeholder="如 U1-BT5-SOC"/>
            名称 <input type="text" name="componentName" size="18" placeholder="可选"/>
            供应商 <input type="text" name="supplierName" size="12" placeholder="可选"/>
            供应商批号 <input type="text" name="supplierLot" size="14" placeholder="DateCode / 料盘号"/>
            数量 <input type="text" name="receivedQty" size="8" value="1"/>
            到货日期 <input type="text" name="receivedDate" size="10" placeholder="yyyy-MM-dd"/>
            <input type="submit" value="保存批次"/>
        </p>
    </form>
    <p class="hint">批次号自动生成（LOT-yyMMdd-NN）；同一料号可登记多个批次。</p>
</fieldset>

<fieldset>
    <legend>批量导入到货（CSV）</legend>
    <form method="post" action="/mfg/control/lotImportCsv">
        <textarea name="csv" rows="6" cols="90">U1-BT5-SOC,主控 SoC,立创商城,DC2410A,120,2026-10-20
C0402-100N,100nF 0402 电容,立创商城,DC2409B,500,2026-10-19</textarea>
        <p class="hint">每行：料号,名称,供应商,供应商批号/DateCode,数量,到货日期(yyyy-MM-dd，可空)</p>
        <p><input type="submit" value="导入"/></p>
    </form>
    <#if (parameters.importedCount)??>
        <p class="ok">已导入 ${parameters.importedCount} 个批次。</p>
    </#if>
</fieldset>

<h2>批次台账</h2>
<form method="get" action="/mfg/control/materials">
    搜索：<input type="text" name="search" size="20" value="${(lr.search)!''}" placeholder="批次 / 料号 / 供应商 / 批号"/>
    元件料号：<input type="text" name="componentCode" size="16" value="${(parameters.componentCode)!''}"/>
    <input type="submit" value="筛选"/>
    <span class="hint">共 ${(lr.totalCount)!0} 条</span>
</form>

<#if lots?has_content>
    <table>
        <tr><th>批次</th><th>元件料号</th><th>名称</th><th>供应商</th><th>供应商批号</th>
            <th class="num">到货</th><th class="num">已发</th><th class="num">剩余</th><th>到货日期</th>
            <th>来料检验</th><th>追溯</th></tr>
        <#list lots as lot>
            <tr>
                <td>${lot.lotId!}</td>
                <td>${lot.componentCode!}</td>
                <td>${lot.componentName!}</td>
                <td>${lot.supplierName!}</td>
                <td>${lot.supplierLot!}</td>
                <td class="num">${((lot.receivedQty)!0)?string("0.##")}</td>
                <td class="num">${((lot.issuedQty)!0)?string("0.##")}</td>
                <td class="num">${((lot.remainingQty)!0)?string("0.##")}</td>
                <td><#if lot.receivedDate??>${lot.receivedDate?string("yyyy-MM-dd")}</#if></td>
                <td>
                    <#if (lot.inspectStatusId!'') == 'MFG_IQC_PASS'><span class="ok">合格</span>
                    <#elseif (lot.inspectStatusId!'') == 'MFG_IQC_CONCESSION'><span class="warn">让步接收</span>
                    <#elseif (lot.inspectStatusId!'') == 'MFG_IQC_FAIL'><span class="error">不合格（不能发料）</span>
                    <#else><span class="hint">待检</span></#if>
                    <#if lot.inspectDate??><span class="hint"> ${lot.inspectDate?string("yyyy-MM-dd")}</span></#if>
                    <#if ((lot.rejectedQty)!0) gt 0><span class="error"> / 不良 ${((lot.rejectedQty)!0)?string("0.##")}</span></#if>
                    <form method="post" action="/mfg/control/lotInspect" class="inline">
                        <input type="hidden" name="lotId" value="${lot.lotId}"/>
                        <select name="inspectStatusId">
                            <option value="MFG_IQC_PENDING"<#if (lot.inspectStatusId!'MFG_IQC_PENDING') == 'MFG_IQC_PENDING'> selected="selected"</#if>>待检</option>
                            <option value="MFG_IQC_PASS"<#if (lot.inspectStatusId!'') == 'MFG_IQC_PASS'> selected="selected"</#if>>合格</option>
                            <option value="MFG_IQC_CONCESSION"<#if (lot.inspectStatusId!'') == 'MFG_IQC_CONCESSION'> selected="selected"</#if>>让步接收</option>
                            <option value="MFG_IQC_FAIL"<#if (lot.inspectStatusId!'') == 'MFG_IQC_FAIL'> selected="selected"</#if>>不合格</option>
                        </select>
                        <input type="text" name="rejectedQty" size="3" value="${((lot.rejectedQty)!0)?string("0.##")}" title="不良数"/>
                        <input type="submit" value="保存"/>
                    </form>
                </td>
                <td><a href="/mfg/control/materials?lotId=${lot.lotId}">查影响范围</a></td>
            </tr>
        </#list>
    </table>
    <p class="hint">来料检验只拦「不合格」批次：待检和让步接收的批次仍可发料；不合格批次要改判或让步接收后才能发。</p>
<#else>
    <p class="hint">还没有批次记录。</p>
</#if>
<#if (lr.pageCount?default(1)) gt 1>
    <p class="pager">
        第 ${(lr.page)!1} / ${(lr.pageCount)!1} 页（共 ${(lr.totalCount)!0} 条）
        <#if (lr.page?default(1)) gt 1>
            <a href="/mfg/control/materials?search=${(lr.search)!''?url}&componentCode=${(parameters.componentCode)!''?url}&page=1">首页</a>
            <a href="/mfg/control/materials?search=${(lr.search)!''?url}&componentCode=${(parameters.componentCode)!''?url}&page=${(lr.page)-1}">上一页</a>
        </#if>
        <#if (lr.page?default(1)) lt (lr.pageCount?default(1))>
            <a href="/mfg/control/materials?search=${(lr.search)!''?url}&componentCode=${(parameters.componentCode)!''?url}&page=${(lr.page)+1}">下一页</a>
            <a href="/mfg/control/materials?search=${(lr.search)!''?url}&componentCode=${(parameters.componentCode)!''?url}&page=${(lr.pageCount)!1}">末页</a>
        </#if>
    </p>
</#if>

<h2>批次追溯（反向：批次 → 工单 → SN）</h2>
<form method="get" action="/mfg/control/materials">
    内部批次：<input type="text" name="lotId" size="16" value="${(parameters.lotId)!''}" placeholder="LOT-261020-01"/>
    或 供应商批号：<input type="text" name="supplierLot" size="16" value="${(parameters.supplierLot)!''}"/>
    <input type="submit" value="追溯"/>
</form>

<#if tracedLots?has_content>
    <p>
        命中批次 ${tracedLots?size} 个，涉及工单 ${tracedOrders?size} 张，
        累计已生产 SN <span class="big">${(tr.totalSnCount)!0}</span> 台。
    </p>
    <#if tracedOrders?has_content>
        <table>
            <tr><th>工单</th><th>产品型号</th><th>状态</th><th>客户/项目</th>
                <th class="num">计划数量</th><th class="num">已建 SN</th><th class="num">不良记录</th>
                <th>推定 SN 区间（FIFO）</th></tr>
            <#list tracedOrders as order>
                <tr>
                    <td><a href="/mfg/control/workOrder?workOrderId=${order.workOrderId}">${order.workOrderId!}</a></td>
                    <td>${order.productCode!}</td>
                    <td>${order.statusId!}</td>
                    <td>${order.customerName!}</td>
                    <td class="num">${(order.plannedQty)?string("0")}</td>
                    <td class="num">${(order.snCount)?string("0")}</td>
                    <td class="num">${(order.defectCount)?string("0")}</td>
                    <td>
                        <#list (order.estimates)![] as est>
                            ${est.componentCode!}: ${est.estimatedSnFrom!} ~ ${est.estimatedSnTo!}
                            <span class="hint">(${est.estimatedSnCount!0}/${est.issueCount!0})</span><br/>
                        </#list>
                    </td>
                </tr>
            </#list>
        </table>
        <p class="hint">推定区间是把工单内的 SN 按发料先后均分得出的估算值（SMT 厂不提供换料记录），
            用于缩小排查范围；若需要精确到单板，请在 SN 页登记实际换件记录。</p>
        <p class="hint">这就是供应商报批次问题时的召回范围：上面所有 SN 都用了该批次。</p>
    <#else>
        <p class="hint">该批次还没有发料给任何工单（或尚未回厂建 SN）。</p>
    </#if>
<#else>
    <#if (parameters.lotId)?? || (parameters.supplierLot)??>
        <p class="hint">没有找到匹配的批次。</p>
    </#if>
</#if>

