<#ftl encoding="UTF-8">
<#--
  One work order: header, operation progress, reporting, serial numbers, defects,
  kitting, material issues and shipments to / from the SMT house.
  Data: detail.workOrder, detail.operations, detail.sns, detail.defects,
        detail.reportedHours, detail.totalQtyOk, detail.totalQtyNg,
        material.lines, material.issues, material.shipments, material.kitComplete
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
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>工单详情 - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.3em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .ok { color: #1a7f37; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
        .big { font-size: 1.5em; font-weight: bold; }
        .sn-list { line-height: 1.5em; }
        .sn-list a { margin-right: .6em; white-space: nowrap; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products">产品与 BOM</a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/delivery">出货单</a>
    <a href="/mfg/control/vendors">外协厂家</a>
    <a href="/mfg/control/claims">售后单</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

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

    <h2>工序进度</h2>
    <#if operations?has_content>
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

    <h2>物料齐套（BOM 需求 vs 已发料）</h2>
    <p>
        <#if (mat.kitComplete)!false>
            <span class="ok">已齐套</span>
        <#else>
            <span class="error">有欠料</span>
        </#if>
        <span class="hint">发往 SMT 的套数合计 ${((mat.outQty)!0)?string("0.##")}，回厂板数 ${((mat.inQty)!0)?string("0.##")}，回厂不良 ${((mat.inDefectQty)!0)?string("0.##")}</span>
    </p>
    <#if lines?has_content>
        <table>
            <tr><th>元件料号</th><th>名称</th><th>关键件</th>
                <th class="num">需求</th><th class="num">已发</th><th class="num">欠料</th></tr>
            <#list lines as line>
                <tr>
                    <td>${line.componentCode!}</td>
                    <td>${line.componentName!}</td>
                    <td><#if (line.isKeyPart!'N') == 'Y'>是<#else>-</#if></td>
                    <td class="num">${((line.requiredQty)!0)?string("0.##")}</td>
                    <td class="num">${((line.issuedQty)!0)?string("0.##")}</td>
                    <td class="num"><#if ((line.shortage)!0) gt 0><span class="error">${line.shortage?string("0.##")}</span><#else>-</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">该型号还没有 BOM，先去「产品与 BOM」导入。</p>
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
</#if>
<script type="text/javascript" src="/mfg/js/copyLabel.js"></script>
</body>
</html>
