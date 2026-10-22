<#ftl encoding="UTF-8">
<#--
  One work order: header, operation progress, reporting, serial numbers, defects,
  kitting, material issues and shipments to / from the SMT house.
  Data: detail.workOrder, detail.operations, detail.sns, detail.defects,
        detail.reportedHours, detail.totalQtyOk, detail.totalQtyNg,
        material.lines, material.issues, material.shipments, material.kitComplete,
        costResult.* (standard vs actual, per operation cost, outsourced fee)
-->
<#assign d = (detail)!{}>
<#assign wo = (d.workOrder)!{}>
<#assign operations = (d.operations)![]>
<#assign sns = (d.sns)![]>
<#assign defects = (d.defects)![]>
<#assign mat = (material)!{}>
<#assign vendorInfo = (d.vendor)!{}>
<#assign vendors = (d.vendors)![]>
<#assign lines = (mat.lines)![]>
<#assign issues = (mat.issues)![]>
<#assign shipments = (mat.shipments)![]>
<#assign atts = ((attachmentResult).attachments)![]>
<#assign cost = (costResult)!{}>
<#assign costOps = ((costResult).operations)![]>
<#assign prodLot = (d.productLot)!{}>
<#assign os = (outsourceResult)!{}>
<#assign fees = (os.fees)![]>
<#assign errList = (errorMessageList)![]>

<#function feeTypeLabel id>
    <#if (id!'') == 'MFG_OUTSRC_SMT'><#return 'SMT 贴片'>
    <#elseif (id!'') == 'MFG_OUTSRC_TOOLING'><#return '模具 / 钢网'>
    <#elseif (id!'') == 'MFG_OUTSRC_ASSEMBLY'><#return '外发组装'>
    <#elseif (id!'') == 'MFG_OUTSRC_TEST'><#return '外协测试'>
    <#else><#return '其他'>
    </#if>
</#function>

<#function basisLabel id>
    <#if (id!'') == 'MFG_OUTSRC_PER_UNIT'><#return '按台'>
    <#elseif (id!'') == 'MFG_OUTSRC_PER_POINT'><#return '按点'>
    <#else><#return '整单'>
    </#if>
</#function>

<#if !wo.workOrderId??>
    <h1>工单不存在</h1>
    <p><a href="/mfg/control/main">← 返回工单列表</a></p>
<#else>
    <h1>${wo.workOrderId} · ${wo.productCode!}</h1>
    <p>
        状态 <b>${wo.statusId!}</b>
        数量 <b>${(wo.plannedQty)?string("0")}</b>
        <#if wo.customerName??>客户 ${wo.customerName}</#if>
        <#if wo.smtVendor??> / SMT 外协 ${wo.smtVendor}</#if>
        <#if wo.dueDate??> / 交期 ${wo.dueDate?string("yyyy-MM-dd")}</#if>
    </p>
    <p>
        累计工时 <span class="big">${(d.reportedHours)?string("0.##")}</span> h
        &nbsp; 良品 <b>${(d.totalQtyOk)?string("0")}</b>
        &nbsp; 不良 <b>${(d.totalQtyNg)?string("0")}</b>
        &nbsp; SN <b>${sns?size}</b>
        &nbsp; 测试通过率
        <b><#if ((d.testedSnCount)!0) gt 0>${((d.testYield)!0)?string("0.#")}%<#else>-</#if></b>
        <span class="hint">(已测 ${(d.testedSnCount)!0} 台 / 其中不合格 ${(d.failedSnCount)!0} 台)</span>
    </p>

    <#if errList?has_content>
        <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
    </#if>

    <h2>成本</h2>
    <table id="workOrderCost">
        <tr><th>项目</th><th class="num">单台</th><th class="num">合计</th></tr>
        <tr><td>标准：材料（BOM 单价）</td>
            <td class="num">${((cost.materialPerUnit)!0)?string("0.00")}</td>
            <td class="num">${(((cost.materialPerUnit)!0) * ((cost.plannedQty)!0))?string("0.00")}</td></tr>
        <tr><td>标准：工时（${((cost.standardHours)!0)?string("0.##")} h × ${((cost.laborRate)!0)?string("0.00")} 元/h<#if (cost.hoursSource!'') == 'ROUTING'>，按工序分摊<#else>，取型号标准工时</#if>）</td>
            <td class="num">${((cost.laborPerUnit)!0)?string("0.00")}</td>
            <td class="num">${(((cost.laborPerUnit)!0) * ((cost.plannedQty)!0))?string("0.00")}</td></tr>
        <tr><td><b>标准：合计</b></td>
            <td class="num"><b>${((cost.standardUnitCost)!0)?string("0.00")}</b></td>
            <td class="num"><b>${((cost.standardTotal)!0)?string("0.00")}</b></td></tr>
        <tr><td><b>实际：已发料（批次单价）</b></td>
            <td class="num"><span class="hint">—</span></td>
            <td class="num"><b>${((cost.issuedMaterialCost)!0)?string("0.00")}</b></td></tr>
        <tr><td><b>实际：报工（${((cost.reportedHours)!0)?string("0.##")} h）</b></td>
            <td class="num"><span class="hint">—</span></td>
            <td class="num"><b>${((cost.actualLaborCost)!0)?string("0.00")}</b></td></tr>
        <tr><td><b>实际：外协加工费</b><#if ((cost.outsourceAmount)!0) lte 0> <span class="hint">未登记</span></#if></td>
            <td class="num"><span class="hint">—</span></td>
            <td class="num"><b>${((cost.outsourceAmount)!0)?string("0.00")}</b></td></tr>
        <tr><td><b>实际：合计（材料 + 工时 + 外协）</b></td>
            <td class="num"><b>${((cost.actualUnitCost)!0)?string("0.00")}</b><span class="hint"> 单台</span></td>
            <td class="num"><b>${((cost.actualTotal)!0)?string("0.00")}</b></td></tr>
        <tr><td>实际 − 标准（按良品 ${((cost.qtyOk)!0)?string("0")} 台，不含外协）</td>
            <td class="num"></td>
            <td class="num"><#if ((cost.differenceExOutsource)!0) gt 0><span class="error">超 ${cost.differenceExOutsource?string("0.00")}</span><#else><span class="ok">省 ${(((cost.differenceExOutsource)!0)?abs)?string("0.00")}</span></#if></td></tr>
        <tr><td>实际 − 标准（含外协）</td>
            <td class="num"></td>
            <td class="num"><#if ((cost.differenceVsStandard)!0) gt 0><span class="error">超 ${cost.differenceVsStandard?string("0.00")}</span><#else><span class="ok">省 ${(((cost.differenceVsStandard)!0)?abs)?string("0.00")}</span></#if></td></tr>
    </table>
    <#if ((cost.issuesWithoutPrice)!0) gt 0 || ((cost.missingPriceCount)!0) gt 0>
        <p class="warn">
            <#if ((cost.missingPriceCount)!0) gt 0>有 ${cost.missingPriceCount} 行 BOM 缺单价。</#if>
            <#if ((cost.issuesWithoutPrice)!0) gt 0>有 ${cost.issuesWithoutPrice} 笔发料的批次没填采购单价。</#if>
            成本会偏低，去产品 BOM 与物料批次里补。
        </p>
    </#if>

    <#if costOps?has_content>
        <table id="operationCost">
            <tr><th class="num">工序号</th><th>工序</th><th class="num">单台标准</th><th class="num">标准工时合计</th>
                <th class="num">实际工时</th><th class="num">标准成本</th><th class="num">实际成本</th><th class="num">差异</th></tr>
            <#list costOps as op>
                <tr>
                    <td class="num">${(op.opSeq)?string("0")}</td>
                    <td><#if (op.opName!'') == ''><span class="warn">工艺外工序</span><#else>${op.opName}</#if></td>
                    <td class="num">${(op.stdHours)?string("0.###")}</td>
                    <td class="num">${(op.stdHoursTotal)?string("0.##")}</td>
                    <td class="num">${(op.actualHours)?string("0.##")}</td>
                    <td class="num">${(op.standardCost)?string("0.00")}</td>
                    <td class="num">${(op.actualCost)?string("0.00")}</td>
                    <td class="num"><#if (op.variance?default(0)) gt 0><span class="error">+${op.variance?string("0.00")}</span><#else><span class="ok">${op.variance?string("0.00")}</span></#if></td>
                </tr>
            </#list>
        </table>
        <p class="hint">标准工时来自该型号的工序模板（按良品数折算），实际工时来自报工；标了「工艺外工序」的是报工时填了模板里没有的工序号。</p>
    </#if>

    <h2>工序进度</h2>    <#if operations?has_content>
        <table>
            <tr><th class="num">工序号</th><th>工序</th><th>工位</th><th class="num">标准工时</th>
                <th class="num">已报工时</th><th class="num">良品</th><th class="num">不良</th></tr>
            <#list operations as op>
                <tr>
                    <td class="num">${(op.opSeq)?string("0")}</td>
                    <td>${op.opName!}</td>
                    <td>${op.workCenter!}</td>
                    <td class="num"><#if op.stdHours??>${op.stdHours?string("0.###")}</#if></td>
                    <td class="num">${(op.reportedHours)?string("0.##")}</td>
                    <td class="num">${(op.qtyOk)?string("0")}</td>
                    <td class="num">${(op.qtyNg)?string("0")}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">该型号还没有工序模板，先去 <a href="/mfg/control/products?productCode=${wo.productCode}">产品与 BOM</a> 添加。</p>
    </#if>

    <fieldset>
        <legend>报工</legend>
        <form method="post" action="/mfg/control/reportCreate">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <p>
                工序
                <select name="opSeq">
                    <#list operations as op>
                        <option value="${op.opSeq}">${op.opSeq} - ${op.opName!}</option>
                    </#list>
                </select>
                作业员 <input type="text" name="operatorName" size="10"/>
                工时(h) <input type="text" name="workHours" size="5" value="1"/>
                良品 <input type="text" name="qtyOk" size="5"/>
                不良 <input type="text" name="qtyNg" size="4" value="0"/>
                SN <input type="text" name="sn" size="16" placeholder="可选"/>
                <input type="submit" value="提交报工"/>
            </p>
        </form>
    </fieldset>

    <fieldset>
        <legend>登记 SN（按 型号-YYMMDD-流水 自动生成）</legend>
        <form method="post" action="/mfg/control/snRegister">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <p>
                数量 <input type="text" name="qty" size="5" value="10"/>
                <input type="submit" value="生成 SN"/>
                <span class="hint">单次最多 500 个</span>
            </p>
        </form>
    </fieldset>

    <h2>SN 清单</h2>
    <#if sns?has_content>
        <p class="sn-list">
            <#list sns as unit>
                <a href="/mfg/control/sn?sn=${unit.sn}">${unit.sn}</a>
            </#list>
        </p>
    <#else>
        <p class="hint">还没有 SN。</p>
    </#if>

    <h2>不良记录</h2>
    <#if defects?has_content>
        <table>
            <tr><th>编号</th><th>SN</th><th class="num">工序</th><th>不良代码</th>
                <th class="num">数量</th><th>责任</th><th class="num">返修工时</th></tr>
            <#list defects as f>
                <tr>
                    <td>${f.defectId!}</td>
                    <td><#if f.sn??><a href="/mfg/control/sn?sn=${f.sn}">${f.sn}</a></#if></td>
                    <td class="num">${(f.opSeq)?string("0")}</td>
                    <td>${f.defectCode!}</td>
                    <td class="num">${(f.qty)?string("0")}</td>
                    <td>${f.responsibility!}</td>
                    <td class="num"><#if f.repairHours??>${f.repairHours?string("0.##")}</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有不良记录。</p>
    </#if>

    <fieldset>
        <legend>登记不良</legend>
        <form method="post" action="/mfg/control/defectCreate">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <p>
                工序
                <select name="opSeq">
                    <#list operations as op>
                        <option value="${op.opSeq}">${op.opSeq} - ${op.opName!}</option>
                    </#list>
                </select>
                SN <input type="text" name="sn" size="16" placeholder="可选"/>
                不良代码 <input type="text" name="defectCode" size="14" value="MISSING_PART"/>
                数量 <input type="text" name="qty" size="4" value="1"/>
                责任
                <select name="responsibility">
                    <option value="HOUSE">自制</option>
                    <option value="SMT">SMT 外协</option>
                    <option value="SUPPLIER">来料</option>
                    <option value="DESIGN">设计</option>
                </select>
                返修工时 <input type="text" name="repairHours" size="5" value="0"/>
                <input type="submit" value="登记不良"/>
            </p>
        </form>
    </fieldset>

    <h2>外协收货信息（邮寄物料用）</h2>
    <#if (vendorInfo.vendorId)??>
        <table>
            <tr><th>厂家</th><td>${vendorInfo.vendorName!}（${vendorInfo.vendorId!}）</td></tr>
            <tr><th>收件人</th><td>${vendorInfo.contactName!}</td></tr>
            <tr><th>手机号</th><td>${vendorInfo.contactMobile!}</td></tr>
            <tr><th>地址</th><td>${vendorInfo.addressText!}</td></tr>
            <tr><th>客户编号</th><td>${vendorInfo.customerCode!}</td></tr>
            <tr><th>邮寄标签</th>
                <td>
                    <textarea rows="5" cols="60" readonly="readonly">${vendorInfo.shippingLabel!}</textarea>
                    <br/><button type="button" onclick="mfgCopyLabel(this)">复制标签</button>
                </td></tr>
        </table>
    <#else>
        <p class="hint">还没有关联外协厂家。在下面选择，或先到「外协厂家」页新增。</p>
    </#if>
    <form method="post" action="/mfg/control/workOrderVendor">
        <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
        <p>
            设置外协厂家
            <select name="vendorId">
                <option value="">（清除）</option>
                <#list vendors as ven>
                    <option value="${ven.vendorId}"<#if (wo.smtVendorId!'') == ven.vendorId> selected="selected"</#if>>${ven.vendorName!} ${ven.contactName!}</option>
                </#list>
            </select>
            <input type="submit" value="保存"/>
        </p>
    </form>
    <h2>外协加工费（按单分摊）</h2>
    <p>
        合计 <b>${((os.outsourceAmount)!0)?string("0.00")}</b> 元
        <span class="hint">（按良品 ${((os.qtyOk)!0)?string("0.##")} 台分摊 = ${((os.outsourcePerUnit)!0)?string("0.00")} 元/台；
            计入工单实际成本与差异，不计入单台标准成本）</span>
    </p>
    <#if fees?has_content>
        <table id="outsourceTable">
            <tr>
                <th class="num">#</th><th>类型</th><th>计价</th><th class="num">单价</th>
                <th class="num">数量</th><th class="num">金额</th><th>供应商</th>
                <th>单据</th><th>日期</th><th>操作</th>
            </tr>
            <#list fees as fee>
                <tr>
                    <td class="num">${fee.seqId?string("0")}</td>
                    <td>${feeTypeLabel(fee.feeTypeId)}</td>
                    <td>${basisLabel(fee.basisId)}</td>
                    <td class="num">${((fee.rate)!0)?string("0.00")}</td>
                    <td class="num">${((fee.qty)!0)?string("0.##")}</td>
                    <td class="num">${((fee.amount)!0)?string("0.00")}</td>
                    <td>${fee.vendorName!}</td>
                    <td>${fee.referenceNo!}</td>
                    <td><#if fee.feeDate??>${fee.feeDate?string("yyyy-MM-dd")}</#if></td>
                    <td>
                        <form method="post" action="/mfg/control/outsourceFeeDelete" class="inline">
                            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
                            <input type="hidden" name="seqId" value="${fee.seqId?string("0")}"/>
                            <input type="submit" value="删除"/>
                        </form>
                    </td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有外协费明细。下面按「按台 / 按点 / 整单」记一条，SMT 按板计价时就填单价 × 板数。</p>
    </#if>
    <form method="post" action="/mfg/control/outsourceFeeSave">
        <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
        <p>
            类型
            <select name="feeTypeId">
                <option value="MFG_OUTSRC_SMT">SMT 贴片</option>
                <option value="MFG_OUTSRC_TOOLING">模具 / 钢网</option>
                <option value="MFG_OUTSRC_ASSEMBLY">外发组装</option>
                <option value="MFG_OUTSRC_TEST">外协测试</option>
                <option value="MFG_OUTSRC_OTHER">其他</option>
            </select>
            计价
            <select name="basisId">
                <option value="MFG_OUTSRC_PER_UNIT">按台</option>
                <option value="MFG_OUTSRC_PER_POINT">按点</option>
                <option value="MFG_OUTSRC_LUMP">整单</option>
            </select>
            单价 <input type="text" name="rate" size="8"/>
            数量 <input type="text" name="qty" size="6" placeholder="留空 = 良品数"/>
            供应商 <input type="text" name="vendorName" size="12"/>
            单据 <input type="text" name="referenceNo" size="12"/>
            日期 <input type="text" name="feeDate" size="10" placeholder="yyyy-mm-dd"/>
            <input type="submit" value="加一条"/>
        </p>
        <p class="hint">「按台」数量留空就按已报工良品数算；「按点」填总点数；「整单」金额 = 单价。
            明细合计会写回工单外协费，有明细时下面那个整单金额就不能再直接改了。</p>
    </form>
    <form method="post" action="/mfg/control/workOrderOutsource">
        <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
        <p>
            整单直填（不拆明细时用）
            <input type="text" name="outsourceAmount" size="10" value="${((cost.outsourceAmount)!0)?string("0.00")}"/>
            <input type="submit" value="保存外协费"/>
        </p>
    </form>

    <h2>物料齐套（BOM 需求 vs 已发料）</h2>
    <p>
        <#if (mat.kitComplete)!false>
            <span class="ok">已齐套</span>
        <#else>
            <span class="error">有欠料</span>
        </#if>
        <span class="hint">发往 SMT 的套数合计 ${((mat.outQty)!0)?string("0.##")}，回厂板数 ${((mat.inQty)!0)?string("0.##")}，回厂不良 ${((mat.inDefectQty)!0)?string("0.##")}</span>
    </p>
    <p>
        <form method="post" action="/mfg/control/workOrderReserve" class="inline">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <input type="submit" value="预留齐套料"/>
        </form>
        <form method="post" action="/mfg/control/workOrderRelease" class="inline">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <input type="submit" value="释放预留"/>
        </form>
        <span class="hint">预留把可用库存占住（可用 = 在库 − 预留），开工单时自动预留一次；发料时自动释放对应部分。</span>
    </p>
    <#if lines?has_content>
        <table id="kittingTable">
            <tr><th>元件料号</th><th>名称</th><th>关键件</th>
                <th class="num">需求</th><th class="num">已发</th><th class="num">已预留</th><th class="num">欠料</th></tr>
            <#list lines as line>
                <tr>
                    <td>${line.componentCode!}</td>
                    <td>${line.componentName!}</td>
                    <td><#if (line.isKeyPart!'N') == 'Y'>是<#else>-</#if></td>
                    <td class="num">${((line.requiredQty)!0)?string("0.##")}</td>
                    <td class="num">${((line.issuedQty)!0)?string("0.##")}</td>
                    <td class="num">${((line.reservedQty)!0)?string("0.##")}</td>
                    <td class="num"><#if ((line.shortage)!0) gt 0><span class="error">${line.shortage?string("0.##")}</span><#else>-</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">该型号还没有 BOM，先去「产品与 BOM」导入。</p>
    </#if>

    <#-- 完工入库：把报工的合格数记成该工单自己的成品批次 FG-WO-… -->
    <h2>完工入库（成品）</h2>
    <p>
        <form method="post" action="/mfg/control/workOrderComplete" class="inline">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            完工日 <input type="text" name="completeDate" size="10" placeholder="yyyy-mm-dd"/>
            <input type="submit" value="完工入库"/>
        </form>
        <span class="hint">按已报工的良品数入库，单位成本 = 实际成本 ÷ 良品数；重复点只补差额，不会重复入库。</span>
    </p>
    <#if prodLot.lotId??>
        <table id="productLot">
            <tr><th>成品批次</th><th class="num">入库</th><th class="num">在库</th>
                <th class="num">已预留</th><th class="num">可发</th><th class="num">单位成本</th><th>入库日</th></tr>
            <tr>
                <td><a href="/mfg/control/stockMoves?lotId=${prodLot.lotId}">${prodLot.lotId}</a></td>
                <td class="num">${(prodLot.receivedQty!0)?string("0.##")}</td>
                <td class="num">${(prodLot.onHandQty!0)?string("0.##")}</td>
                <td class="num">${(prodLot.reservedQty!0)?string("0.##")}</td>
                <td class="num">${(prodLot.availableQty!0)?string("0.##")}</td>
                <td class="num"><#if prodLot.unitPrice??>${prodLot.unitPrice?string("0.00")}<#else><span class="hint">—</span></#if></td>
                <td><#if prodLot.receivedDate??>${prodLot.receivedDate?string("yyyy-MM-dd")}</#if></td>
            </tr>
        </table>
        <p class="hint">出货时按台从该批次自动扣减；点击「完工入库」可把新增的报工再补进这批货。</p>
    <#else>
        <p class="hint">还没完工入库：先报工，再点上面的按钮，成品就会进库存，随后才能开出货单。</p>
    </#if>

    <h2>发料记录（批次）</h2>
    <#if issues?has_content>
        <table>
            <tr><th>批次</th><th>供应商</th><th>供应商批号</th><th>元件料号</th>
                <th class="num">数量</th><th>位号</th><th>SN 区间</th></tr>
            <#list issues as it>
                <tr>
                    <td><a href="/mfg/control/materials?lotId=${it.lotId}">${it.lotId!}</a></td>
                    <td>${it.supplierName!}</td>
                    <td>${it.supplierLot!}</td>
                    <td>${it.componentCode!}</td>
                    <td class="num">${((it.qty)!0)?string("0.##")}</td>
                    <td>${it.designators!}</td>
                    <td><#if it.snFrom??>${it.snFrom}</#if><#if it.snTo??> ~ ${it.snTo}</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有发料记录。</p>
    </#if>

    <fieldset>
        <legend>登记发料</legend>
        <form method="post" action="/mfg/control/issueSave">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <p>
                元件料号 <input type="text" name="componentCode" size="16" placeholder="如 U1-BT5-SOC"/>
                批次 <input type="text" name="lotId" size="14" placeholder="LOT-261020-01"/>
                数量 <input type="text" name="qty" size="6" value="1"/>
                位号 <input type="text" name="designators" size="12" placeholder="可选"/>
                <input type="submit" value="发料"/>
            </p>
        </form>
        <p class="hint">批次在「<a href="/mfg/control/materials">物料批次</a>」页登记（料号 + 供应商批号 + 数量）。</p>
    </fieldset>

    <h2>外协收发（快递）</h2>
    <#if shipments?has_content>
        <table>
            <tr><th>方向</th><th>日期</th><th class="num">数量</th><th class="num">不良</th>
                <th>快递</th><th>单号</th><th>备注</th></tr>
            <#list shipments as sh>
                <tr>
                    <td><#if sh.directionId! == 'MFG_SHIP_IN'>回厂收货<#else>发往 SMT</#if></td>
                    <td><#if sh.shipmentDate??>${sh.shipmentDate?string("yyyy-MM-dd")}</#if></td>
                    <td class="num">${((sh.qty)!0)?string("0.##")}</td>
                    <td class="num">${((sh.defectQty)!0)?string("0.##")}</td>
                    <td>${sh.carrier!}</td>
                    <td>${sh.trackingNo!}</td>
                    <td>${sh.note!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有收发记录。</p>
    </#if>

    <fieldset>
        <legend>登记收发</legend>
        <form method="post" action="/mfg/control/shipmentSave">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <p>
                方向
                <select name="directionId">
                    <option value="MFG_SHIP_OUT">发往 SMT（物料）</option>
                    <option value="MFG_SHIP_IN">回厂收货（板子）</option>
                </select>
                数量 <input type="text" name="qty" size="6" value="1"/>
                不良 <input type="text" name="defectQty" size="5" value="0"/>
                快递 <input type="text" name="carrier" size="10" value="顺丰"/>
                单号 <input type="text" name="trackingNo" size="16"/>
                <input type="submit" value="保存"/>
            </p>
        </form>
    </fieldset>

    <fieldset>
        <legend>导入测试结果（CSV）</legend>
        <form method="post" action="/mfg/control/testImportCsv">
            <input type="hidden" name="workOrderId" value="${wo.workOrderId}"/>
            <textarea name="csv" rows="4" cols="90"># sn,station,item,value,unit,pass
ITMFG-261021-0001,FCT,VBAT,3.72,V,Y
ITMFG-261021-0001,FCT,IQ,12.4,mA,Y</textarea>
            <p class="hint">每行：SN,工位,测试项,测量值,单位,判定(Y/N)；未知 SN 的行会被跳过。</p>
            <p><input type="submit" value="导入"/></p>
        </form>
    </fieldset>

    <h3>工单附件</h3>
    <#include "attachmentBlock.ftl">
    <#assign attOwner = wo.workOrderId>
    <#assign attBack = '/mfg/control/workOrder?workOrderId=' + attOwner>
    <@attachmentBlock 'MFG_ATT_WORK_ORDER' attOwner attBack '首件确认 / 不良照片 / 外协快递单' atts/>
</#if>
<script type="text/javascript" src="/mfg/js/copyLabel.js"></script>

