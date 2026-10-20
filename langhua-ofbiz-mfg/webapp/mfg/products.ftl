<#ftl encoding="UTF-8">
<#--
  Product models: BOM import and routing maintenance.
  Data: listResult.products, detail.product, detail.bomItems, detail.routings
-->
<#assign lr = (listResult)!{}>
<#assign products = (lr.products)![]>
<#assign d = (detail)!{}>
<#assign product = (d.product)!{}>
<#assign bomItems = (d.bomItems)![]>
<#assign routings = (d.routings)![]>
<#assign errList = (errorMessageList)![]>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>产品与 BOM - Hardware Assembly</title>
    <style type="text/css">
        body { font-family: Arial, Helvetica, sans-serif; margin: 1.5em; color: #222; }
        h1 { font-size: 1.4em; } h2 { font-size: 1.05em; margin-top: 1.4em; }
        table { border-collapse: collapse; margin-top: .6em; }
        th, td { border: 1px solid #ccc; padding: 4px 10px; text-align: left; }
        th { background: #f3f3f3; }
        .num { text-align: right; }
        .nav a { margin-right: 1em; }
        .error { color: #b00000; font-weight: bold; }
        .ok { color: #1a7f37; font-weight: bold; }
        .hint { color: #666; font-size: .85em; }
        fieldset { border: 1px solid #ddd; padding: .6em 1em; margin-top: 1em; }
        textarea { width: 46em; height: 7em; font-family: Consolas, monospace; font-size: .85em; }
        code { background: #f6f6f6; padding: 1px 4px; }
    </style>
</head>
<body>
<p class="nav">
    <a href="/mfg/control/main">工单</a>
    <a href="/mfg/control/products"><b>产品与 BOM</b></a>
    <a href="/mfg/control/materials">物料批次</a>
    <a href="/mfg/control/shortage">齐套预警</a>
    <a href="/mfg/control/delivery">出货单</a>
    <a href="/mfg/control/vendors">外协厂家</a>
    <a href="/mfg/control/claims">售后单</a>
    <a href="/mfg/control/dashboard">看板</a>
    <a href="/mfg/control/sn">SN 查询</a>
</p>

<h1>产品与 BOM</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
<#if parameters.importedCount??>
    <p class="ok">已导入 ${parameters.importedCount} 行 BOM。</p>
</#if>

<fieldset>
    <legend>新建 / 更新型号</legend>
    <form method="post" action="/mfg/control/productSave">
        <p>
            型号编码 <input type="text" name="productCode" size="10" value="${product.productCode!''}"/>
            名称 <input type="text" name="productName" size="22" value="${product.productName!''}"/>
            SN 前缀 <input type="text" name="snPrefix" size="8" value="${product.snPrefix!''}"/>
            标准工时/台 <input type="text" name="standardHours" size="6" value="<#if product.standardHours??>${product.standardHours?string("0.##")}</#if>"/>
            <input type="submit" value="保存"/>
        </p>
    </form>
</fieldset>

<h2>型号列表</h2>
<#if products?has_content>
    <table>
        <tr>
            <th>型号</th>
            <th>名称</th>
            <th>SN 前缀</th>
            <th class="num">标准工时</th>
            <th class="num">BOM 行</th>
            <th class="num">工序数</th>
            <th>状态</th>
        </tr>
        <#list products as p>
            <tr>
                <td><a href="/mfg/control/products?productCode=${p.productCode}">${p.productCode}</a></td>
                <td>${p.productName!}</td>
                <td>${p.snPrefix!}</td>
                <td class="num"><#if p.standardHours??>${p.standardHours?string("0.##")}</#if></td>
                <td class="num">${p.bomCount}</td>
                <td class="num">${p.routingCount}</td>
                <td>${p.statusId!}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有产品型号，先在上面的表单里创建一个（例如 <code>BT5</code>）。</p>
</#if>

<#if product.productCode??>
    <h2>BOM 导入（${product.productCode}）</h2>
    <fieldset>
        <legend>粘贴 CSV：元件料号,名称,用量,位号,是否关键料(Y/N)</legend>
        <form method="post" action="/mfg/control/bomImport">
            <input type="hidden" name="productCode" value="${product.productCode}"/>
            <p><textarea name="csv">R0402-10K,10K 0402 电阻,4,R1-R4,N
C0402-100N,100nF 0402 电容,6,C1-C6,N
U1-BT5-SOC,主控 SoC,1,U1,Y
BAT-18650,18650 电池,1,BT1,Y</textarea></p>
            <p>
                <label><input type="checkbox" name="replace" value="N"/> 追加（不勾选则先清空原 BOM）</label>
                <input type="submit" value="导入 BOM"/>
            </p>
        </form>
    </fieldset>

    <h2>当前 BOM（${bomItems?size} 行）</h2>
    <#if bomItems?has_content>
        <table>
            <tr><th class="num">行号</th><th>料号</th><th>名称</th><th class="num">用量</th><th>位号</th><th>关键料</th></tr>
            <#list bomItems as item>
                <tr>
                    <td class="num">${(item.lineSeq)?string("0")}</td>
                    <td>${item.componentCode!}</td>
                    <td>${item.componentName!}</td>
                    <td class="num">${(item.qty)?string("0.##")}</td>
                    <td>${item.designators!}</td>
                    <td>${item.isKeyPart!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">该型号还没有 BOM。</p>
    </#if>

    <h2>工序模板（${routings?size} 个）</h2>
    <#if routings?has_content>
        <table>
            <tr><th class="num">工序号</th><th>工序名</th><th>工位</th><th class="num">标准工时</th></tr>
            <#list routings as r>
                <tr>
                    <td class="num">${(r.opSeq)?string("0")}</td>
                    <td>${r.opName!}</td>
                    <td>${r.workCenter!}</td>
                    <td class="num"><#if r.stdHours??>${r.stdHours?string("0.###")}</#if></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有工序，用下面的表单添加（建议：10 组装、20 老化、30 测试、40 包装）。</p>
    </#if>

    <fieldset>
        <legend>新增 / 更新工序</legend>
        <form method="post" action="/mfg/control/routingSave">
            <input type="hidden" name="productCode" value="${product.productCode}"/>
            <p>
                工序号 <input type="text" name="opSeq" size="4" value="10"/>
                工序名 <input type="text" name="opName" size="14" value="组装"/>
                工位 <input type="text" name="workCenter" size="10"/>
                标准工时/台 <input type="text" name="stdHours" size="6"/>
                <input type="submit" value="保存工序"/>
            </p>
        </form>
    </fieldset>
</#if>
</body>
</html>
