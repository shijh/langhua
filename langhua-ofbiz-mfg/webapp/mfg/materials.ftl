<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
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

<#-- Every action inside a row of the ledger comes back through a redirect. Carry the filter and the
     page along, so saving one row leaves the reader on the page he was working on instead of page
     one of the whole ledger. -->
<#macro keepView>
    <input type="hidden" name="backSearch" value="${(lr.search)!}"/>
    <input type="hidden" name="backPage" value="${(lr.page)!1}"/>
    <input type="hidden" name="backPageSize" value="${(lr.pageSize)!20}"/>
    <input type="hidden" name="backComponentCode" value="${(parameters.componentCode)!}"/>
</#macro>

<h1>物料批次</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>登记批次（采购到货）</legend>
    <form method="post" action="/mfg/control/lotSave">
        <p>
            元件料号 <input type="text" name="componentCode" size="16" placeholder="如 U1-BT5-SOC"/>
            名称 <input type="text" name="partName" size="18" placeholder="可选"/>
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
    <legend>料号重命名（批次 / 发料 / 到货单行 / BOM / 售后换件 / 安全库存一起改）</legend>    <form method="post" action="/mfg/control/partCodeRename">
        <p>
            旧料号 <input type="text" name="oldCode" size="42" value="${(lr.search)!''}"/>
            新料号 <input type="text" name="newCode" size="42" placeholder="5.5V1.5F C型 21*7.5"/>
            <input type="submit" value="重命名"/>
            <span class="hint">这里会把这个料号的<b>所有</b>行一起改（批次 / 发料 / 到货单行 / BOM / 售后换件 / 安全库存）——
                同一个料号往往对应很多个批次，只想改某一个批次时用下面台账「元件料号」那格的「改」。</span>
        </p>
    </form>
    <#if (parameters.renamed)??>
        <p class="ok">已把 ${parameters.renamed} 行料号改成 <code>${(lr.search)!''}</code>。</p>
    </#if>
</fieldset>

<fieldset>
    <legend>批量导入到货（CSV）</legend>    <form method="post" action="/mfg/control/lotImportCsv">
        <textarea name="csv" rows="6" cols="90">U1-BT5-SOC,主控 SoC,立创商城,DC2410A,120,2026-10-20
C0402-100N,100nF 0402 电容,立创商城,DC2409B,500,2026-10-19</textarea>
        <p class="hint">每行：料号,名称,供应商,供应商批号/DateCode,数量,到货日期(yyyy-MM-dd，可空)</p>
        <p><input type="submit" value="导入"/></p>
    </form>
    <#if (parameters.importedCount)??>
        <p class="ok">已导入 ${parameters.importedCount} 个批次。</p>
    </#if>
</fieldset>

<fieldset id="splitBox">
    <legend>拆包（一个批次拆成多个料号）</legend>
    <form method="post" action="/mfg/control/lotSplit">
        <@keepView/>
        <p>原批次：<input type="text" name="lotId" size="18" value="${(parameters.lotId)!''}"
                        placeholder="LOT-261025-844"/>
            这一包实际多少件：<input type="text" name="packQty" size="8" value=""
                        title="可空。空着 = 清单里就是这一包的全部；填了 = 原批次按这个数修正，拆完剩下的留在这里"/>
            <span class="hint">拆出去的料号各建一个批次，<b>单价、供应商、批号、到货日、检验状态都沿用原批次</b>；
                原批次按拆走的数量减少（记一条调整流水）。子批次会记下“拆自哪个批次”，台账里能点回去。</span></p>
        <textarea name="lines" rows="8" cols="90" placeholder="R-1/4W-100R,1/4W 金属膜电阻 100Ω,20&#10;R-1/4W-1K,1/4W 金属膜电阻 1kΩ,20&#10;R-1/4W-10K,20"></textarea>
        <p class="hint">每行：料号,名称,数量（<b>名称可空</b>，空着就沿用原批次的名称；只写「料号,数量」也行）。
            一次可以粘上上百行，<b>全角逗号也认</b>，表头行会跳过，料号重复的行自动去重（留第一行并告诉你去掉了哪几行）。
            <b>一包按「1 件」入库的也可以直接拆</b>：账面只有 1 件时，系统先按清单件数把原批次的数量修正过来，再拆；
            其他情况（比如账上 210 件却要拆 2600）仍然会拒绝，那就先用那一行的「点数修正」。成品批次不能拆。</p>
        <p><input type="submit" value="拆包"/></p>
    </form>
    <#if parameters.splitLot??>
        <p class="ok">已把 ${parameters.splitLot} 拆成 <b>${parameters.splitCount}</b> 个批次，共
            ${(parameters.splitQty)!'0'} 件（流水号 ${(parameters.splitRef)!''}，单价沿用
            ${(parameters.unitPrice)!'0'}）。新批次可以在下面的台账里搜料号看到。</p>
    </#if>
    <#if (parameters.splitDropped!'0') != '0'>
        <p class="warn">粘的清单里有 ${parameters.splitDropped} 行料号重复，<b>已经自动去重</b>（同料号只留第一行）：
            ${(parameters.splitDroppedNote)!''}</p>
    </#if>
    <#if parameters.splitRestated??>
        <p class="ok">原批次的数量已按这一包的实际件数修正为 ${parameters.splitRestated} 件
            （记一条调整流水，单价跟着重算），再拆出去；剩下的还在原批次上，以后点数修正也照常能用。</p>
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
            <th>来料检验</th><th>点数修正</th><th>追溯</th></tr>
        <#list lots as lot>
            <tr data-lot-row="${lot.lotId}"<#if (lot.splitFromLotId!'')?has_content> data-split-child="${lot.splitFromLotId}"</#if>>
                <td>${lot.lotId!}
                    <#-- one click brings the batch into the split box above: a pack of 2600 resistors
                         has to become one lot per value -->
                    <a class="hint" href="/mfg/control/materials?lotId=${lot.lotId}#splitBox" title="拆包">拆</a>
                    <#if (lot.scanShotId!'')?has_content>
                        <#-- the picture the shop printed on the order screenshot this batch came in with:
                             the shop's own photo is what tells one reel from another at a glance -->
                        <br/><a href="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${lot.scanShotId?url}"
                                target="_blank" title="订单截图里的商品图"><img class="shot"
                                data-lot-shot="${lot.scanShotId}"
                                src="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${lot.scanShotId?url}&amp;thumb=1"/></a>
                    </#if>
                    <#if (lot.splitFromLotId!'')?has_content>
                        <br/><span class="hint">拆自
                            <a href="/mfg/control/materials?search=${lot.splitFromLotId?url}">${lot.splitFromLotId}</a></span>
                    </#if>
                </td>
                <td>
                    <#-- one lot, one part number: this corrects the parcel in front of you, not every
                         row that happens to carry the same code. The field is called partCode, not
                         componentCode: the page also filters by a componentCode, and a posted field of
                         that name would be picked up by the redirect and fill in the filter -->
                    <form method="post" action="/mfg/control/lotCodeChange" class="inline">
                        <input type="hidden" name="lotId" value="${lot.lotId}"/>
                        <@keepView/>
                        <input type="text" name="partCode" size="16" value="${(lot.componentCode)!}"
                               title="${(lot.componentCode)!}"/>
                        <input type="submit" value="改"/>
                    </form>
                    <#-- 规格书 / 图纸挂在这个料号上（不是这一批）：有文件就先给文件，维护放在旁边 -->
                    <#if (lot.partDocSeq!'')?has_content>
                        <a data-part-docs="${(lot.componentCode)!}" data-part-doc-view="${lot.partDocSeq}"
                           target="_blank" title="${(lot.partDocName)!'资料'}"
                           href="/mfg/control/attachment?ownerTypeId=MFG_ATT_PART&amp;ownerId=${((lot.componentCode)!'')?url}&amp;seq=${lot.partDocSeq}">看资料</a>
                        <a class="hint" data-part-docs-open="${(lot.componentCode)!}"
                           href="/mfg/control/partDocs?componentCode=${((lot.componentCode)!'')?url}"
                           title="上传新版 / 删除传错的资料">维护<#if ((lot.partDocCount)!0) gt 1>（${lot.partDocCount}）</#if></a>
                    <#else>
                        <a class="hint" data-part-docs="${(lot.componentCode)!}"
                           href="/mfg/control/partDocs?componentCode=${((lot.componentCode)!'')?url}"
                           title="这个料号的资料（PDF / 图片）">资料</a>
                    </#if>
                </td>
                <td>
                    <#-- a lot imported from a shop order carries the whole article title; a readable
                         name is what makes the right lot recognizable in the BOM's part number list -->
                    <form method="post" action="/mfg/control/lotEdit" class="inline">
                        <input type="hidden" name="lotId" value="${lot.lotId}"/>
                        <@keepView/>
                        <input type="text" name="partName" size="22" value="${(lot.componentName)!}"
                               title="名称（在 BOM 料号下拉、发料、追溯里都用这个名字）"/>
                        <input type="text" name="note" size="10" value="${(lot.note)!}" placeholder="备注"/>
                        <input type="submit" value="改"/>
                    </form>
                </td>
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
                        <@keepView/>
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
                <td>
                    <#-- a shop sells "one order" of prototype boards and ships three to five of them: the
                         count of the parcel is the real quantity, the money stays what was paid -->
                    <form method="post" action="/mfg/control/lotRecount" class="inline">
                        <input type="hidden" name="lotId" value="${lot.lotId}"/>
                        <@keepView/>
                        <input type="text" name="actualQty" size="4" value="${((lot.receivedQty)!0)?string("0.##")}"
                               title="点到的实际数量"/>
                        <input type="text" name="note" size="8" placeholder="备注"/>
                        <input type="submit" value="改"/>
                    </form>
                </td>
            </tr>
        </#list>
    </table>
    <p class="hint">来料检验只拦「不合格」批次：待检和让步接收的批次仍可发料；不合格批次要改判或让步接收后才能发。</p>
    <#if parameters.recounted??>
        <p class="ok">批次 ${parameters.recounted} 已按点数改成 ${(parameters.recountQty)!''}：钱没变，单价跟着数量重算，库存按新数量算。流水里多了一条「调整」（备注写清了改前改后）。</p>
    </#if>
    <#if parameters.editedLot??>
        <p class="ok">批次 ${parameters.editedLot} 的名称/备注已保存（料号要改的话用上面的「料号重命名」）。</p>
    </#if>
    <#if parameters.renamedLot??>
        <p class="ok">批次 ${parameters.renamedLot} 的料号已改成 <code>${(parameters.newCode)!''}</code>
            （<b>只改了这一行</b>，它的发料与到货单行也一起改；其他同料号的批次没动）。
            <#if ((parameters.otherLots!'0') == '0') && ((parameters.referencesUpdated!'0') != '0')>
                用了旧料号 <code>${(parameters.oldCode)!''}</code> 的地方也跟过去了：
                产品 BOM ${(parameters.bomLinesUpdated)!'0'} 行、研发 BOM ${(parameters.devBomLinesUpdated)!'0'} 行、
                售后换件 ${(parameters.replacementsUpdated)!'0'} 行<#if (parameters.stockSettingMoved!'') == 'Y'>、安全库存 1 项</#if>。
            </#if></p>
        <#if (parameters.otherLots!'0') != '0'>
            <p class="warn">旧料号 <code>${(parameters.oldCode)!''}</code> 还有 <b>${parameters.otherLots}</b> 个批次在用，
                所以 BOM、安全库存、售后换件这些地方<b>没动</b>（改了会把那些批次的料从 BOM 里挤掉）。
                要让这个料号整体改名，用上面的「料号重命名」。</p>
        </#if>
    </#if>
    <#if parameters.recounted?? || parameters.editedLot?? || parameters.renamedLot??>
        <p class="hint">表格现在只显示刚改的这一行（台账按到货时间排序、还会翻页，原来那一页上常常找不到它）。
            把上面的搜索框清空、再点「筛选」就回到整张台账。</p>
    </#if>
    <p class="hint">「点数修正」用在<b>一个订单里有几块/几片</b>的料上（PCB 打样最常见：订单写 ¥21.00 x1，实际发 3~5 块）：
        拆包点清楚后在这里填真实数量。金额不变，单价自动变成 实付 ÷ 数量（如 21 元 5 块 = 4.20/块），
        到货单上那一行也跟着改。数量变多就多出库存，变少就少（流水里会多一条「调整」，谁数的、为什么改都在）。
        <br/><b>名称 / 备注</b>在「名称」格里直接改（改了之后 BOM 的料号下拉、发料、追溯里都是这个名字，好认）；
        <b>料号</b>在「元件料号」格里改（<b>只改这一行</b>，它的发料与到货单行一起改；同一个料号的其他批次不动）；
        这个料号如果<b>再没有别的批次</b>，产品 BOM、研发 BOM、安全库存、售后换件里写着旧料号的行会跟着改成新料号；
        还有别的批次在用就不动（以免把那些批次的料从 BOM 里挤掉），那种情况用上方的「料号重命名」。</p>
<#else>
    <p class="hint">还没有批次记录。</p>
</#if>
<@pg.pager action="/mfg/control/materials" query=('search=' + ((lr.search)!'')?url + '&' + 'componentCode=' + ((parameters.componentCode)!'')?url)
    page=(lr.page)!1 pageCount=(lr.pageCount)!1 total=(lr.totalCount)!0 unit="条" pageSize=(lr.pageSize)!20/>

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

