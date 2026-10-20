<#ftl encoding="UTF-8">
<#--
  One work order: header, operation progress, reporting, serial numbers, defects.
  Data: detail.workOrder, detail.operations, detail.sns, detail.defects,
        detail.reportedHours, detail.totalQtyOk, detail.totalQtyNg
-->
<#assign d = (detail)!{}>
<#assign wo = (d.workOrder)!{}>
<#assign operations = (d.operations)![]>
<#assign sns = (d.sns)![]>
<#assign defects = (d.defects)![]>
<#assign ra = (requestAttributes)!{}>
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
    </p>

    <#if ra["_ERROR_MESSAGE_"]?? && ra["_ERROR_MESSAGE_"]?has_content>
        <p class="error">${ra["_ERROR_MESSAGE_"]}</p>
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
</#if>
</body>
</html>
