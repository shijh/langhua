<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Product models: photos, BOM versions and routing maintenance.
  Data: listResult.products, detail.product, detail.bomItems, detail.bomVersionId, detail.routings,
        versionResult.bomVersions, imageResult.images
-->
<#assign lr = (listResult)!{}>
<#assign products = (lr.products)![]>
<#assign d = (detail)!{}>
<#assign product = (d.product)!{}>
<#assign bomItems = (d.bomItems)![]>
<#assign currentVersionId = (d.bomVersionId)!''>
<#assign routings = (d.routings)![]>
<#assign vr = (versionResult)!{}>
<#assign versions = (vr.bomVersions)![]>
<#assign ir = (imageResult)!{}>
<#assign images = (ir.attachments)![]>
<#assign c = (costResult)!{}>
<#assign costDenied = ((c.denied)!'N') == 'Y'>
<#assign errList = (errorMessageList)![]>

<#function stageLabel stageId>
    <#if (stageId!'') == 'MFG_STAGE_CONCEPT'><#return '概念'>
    <#elseif (stageId!'') == 'MFG_STAGE_DESIGN'><#return '设计'>
    <#elseif (stageId!'') == 'MFG_STAGE_PROTOTYPE'><#return '打样'>
    <#elseif (stageId!'') == 'MFG_STAGE_TRIAL'><#return '试产'>
    <#elseif (stageId!'') == 'MFG_STAGE_MASS'><#return '量产'>
    <#else><#return '未设置'>
    </#if>
</#function>
<#function versionLabel statusId>
    <#if (statusId!'') == 'MFG_BOM_RELEASED'><#return '使用中'>
    <#elseif (statusId!'') == 'MFG_BOM_DRAFT'><#return '草稿'>
    <#elseif (statusId!'') == 'MFG_BOM_OBSOLETE'><#return '已废止'>
    <#else><#return '${statusId!}'>
    </#if>
</#function>

<h1>产品与 BOM</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
<#if parameters.importedCount??>
    <p class="ok">已导入 ${parameters.importedCount} 行 BOM<#if (parameters.bomFormat!'') != ''>（${parameters.bomFormat}）</#if>。</p>
</#if>
<#if parameters.lineSeq??>
    <p class="ok">已保存 BOM 行 ${parameters.lineSeq}。</p>
</#if>

<fieldset>
    <legend>新建 / 更新型号</legend>
    <form method="post" action="/mfg/control/productSave">
        <p>
            型号编码 <input type="text" name="productCode" size="10" value="${product.productCode!''}"/>
            名称 <input type="text" name="productName" size="22" value="${product.productName!''}"/>
            SN 前缀 <input type="text" name="snPrefix" size="8" value="${product.snPrefix!''}"/>
            标准工时/台 <input type="text" name="standardHours" size="6" value="<#if product.standardHours??>${product.standardHours?string("0.##")}</#if>" data-hours-source="${(product.standardHoursSource)!''}"/><#if (product.standardHoursSource!'') == 'LEARNING_CURVE'><span class="hint">（学习曲线回填，基准 ${((product.standardHoursBaseQty)!0)?string("0.##")} 台，估算 ${(product.standardHoursEstimateRef)!''}）</span><#else><span class="hint">（手工填写）</span></#if>
            工时费率 <input type="text" name="laborRate" size="6" value="<#if product.laborRate??>${product.laborRate?string("0.##")}</#if>" title="元/小时，用于成本核算"/>
            保修月数 <input type="text" name="warrantyMonths" size="4" value="${product.warrantyMonths!12}"/>
            售价 <input type="text" name="salePrice" size="7" value="${product.salePrice!0}"/>
            开发阶段
            <select name="devStageId">
                <#list ['MFG_STAGE_CONCEPT','MFG_STAGE_DESIGN','MFG_STAGE_PROTOTYPE','MFG_STAGE_TRIAL','MFG_STAGE_MASS'] as stage>
                    <option value="${stage}"<#if (product.devStageId!'') == stage> selected="selected"</#if>>${stageLabel(stage)}</option>
                </#list>
            </select>
            <input type="submit" value="保存"/>
        </p>
    </form>
</fieldset>

<h2>型号列表</h2>
<form method="get" action="/mfg/control/products">
    搜索 <input type="text" name="search" size="24" value="${(lr.search)!''}" placeholder="型号 / 名称 / SN 前缀"/>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(lr.totalCount)!0} 条</span>
</form>
<#if products?has_content>
    <table>
        <tr>
            <th></th>
            <th>型号</th>
            <th>名称</th>
            <th>SN 前缀</th>
            <th>阶段</th>
            <th>当前 BOM</th>
            <th class="num">BOM 行</th>
            <th class="num">版本数</th>
            <th class="num">照片</th>
            <th class="num">工序数</th>
            <th>状态</th>
        </tr>
        <#list products as p>
            <tr>
                <td><#if p.latestImageSeq??><img src="/mfg/control/productImage?productCode=${p.productCode}&seq=${p.latestImageSeq}&thumb=1" alt="${p.productCode}" width="44" height="44" style="object-fit: cover; border: 1px solid #3c3c3c;"/><#else><span class="hint">-</span></#if></td>
                <td><a href="/mfg/control/products?productCode=${p.productCode}">${p.productCode}</a></td>
                <td>${p.productName!}</td>
                <td>${p.snPrefix!}</td>
                <td>${stageLabel(p.devStageId!'')}</td>
                <td><#if (p.currentBomVersionId!'') != ''>${p.currentBomVersionId}<#else><span class="hint">未建 BOM</span></#if></td>
                <td class="num">${p.bomCount}</td>
                <td class="num">${p.versionCount}</td>
                <td class="num">${p.imageCount}</td>
                <td class="num">${p.routingCount}</td>
                <td>${p.statusId!}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有产品型号，先在上面的表单里创建一个（例如 <code>BT5</code>）。</p>
</#if>
<#if (lr.pageCount?default(1)) gt 1>
    <p class="pager">
        第 ${(lr.page)!1} / ${(lr.pageCount)!1} 页（共 ${(lr.totalCount)!0} 条）
        <#if (lr.page?default(1)) gt 1>
            <a href="/mfg/control/products?search=${(lr.search)!''?url}&page=1">首页</a>
            <a href="/mfg/control/products?search=${(lr.search)!''?url}&page=${(lr.page)-1}">上一页</a>
        </#if>
        <#if (lr.page?default(1)) lt (lr.pageCount?default(1))>
            <a href="/mfg/control/products?search=${(lr.search)!''?url}&page=${(lr.page)+1}">下一页</a>
            <a href="/mfg/control/products?search=${(lr.search)!''?url}&page=${(lr.pageCount)!1}">末页</a>
        </#if>
    </p>
</#if>

<#if product.productCode??>
    <h2>产品照片（${images?size} 个）</h2>
    <#include "attachmentBlock.ftl">
    <#assign attOwner = product.productCode>
    <#assign attBack = '/mfg/control/products?productCode=' + attOwner>
    <@attachmentBlock 'MFG_ATT_PRODUCT' attOwner attBack '产品照片' images/>

    <h2>BOM 版本（${versions?size} 个）</h2>
    <#if versions?has_content>
        <table>
            <tr><th>版本</th><th>状态</th><th>行数</th><th>生效日</th><th>来源</th><th>变更单</th><th>操作</th></tr>
            <#list versions as v>
                <tr<#if v.bomVersionId == currentVersionId> data-current="Y"</#if>>
                    <td><#if v.bomVersionId == currentVersionId><b>${v.bomVersionId}</b><#else>${v.bomVersionId}</#if></td>
                    <td><#if v.statusId == 'MFG_BOM_RELEASED'><span class="ok">${versionLabel(v.statusId)}</span><#else>${versionLabel(v.statusId)}</#if></td>
                    <td class="num">${v.itemCount}</td>
                    <td><#if v.effectiveDate??>${v.effectiveDate?string("yyyy-MM-dd")}</#if></td>
                    <td>${v.basisVersionId!}</td>
                    <td>${v.ecoId!}</td>
                    <td>
                        <#if v.bomVersionId != currentVersionId>
                            <form method="post" action="/mfg/control/bomVersionActivate" class="inline">
                                <input type="hidden" name="bomVersionId" value="${v.bomVersionId}"/>
                                <input type="submit" value="启用此版本"/>
                            </form>
                        <#else>
                            <span class="hint">当前使用</span>
                        </#if>
                    </td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有 BOM 版本，导入 BOM 时会自动创建 V1。</p>
    </#if>
    <form method="post" action="/mfg/control/bomVersionCreate">
        <input type="hidden" name="productCode" value="${product.productCode}"/>
        <p>
            新版本说明 <input type="text" name="note" size="30" value=""/>
            <label><input type="checkbox" name="copyCurrent" value="Y" checked="checked"/> 复制当前 BOM 行</label>
            <input type="submit" value="新建下一版"/>
        </p>
    </form>

    <h2>BOM 导入（${product.productCode}）</h2>
    <fieldset>
        <legend>选文件或粘贴 BOM：本系统 CSV 或 Fritzing 导出（自动识别）<#if currentVersionId != ''>，导入到 <b>${currentVersionId}</b></#if></legend>
        <form method="post" action="/mfg/control/bomImport" enctype="multipart/form-data">
            <input type="hidden" name="productCode" value="${product.productCode}"/>
            <#if currentVersionId != ''><input type="hidden" name="bomVersionId" value="${currentVersionId}"/></#if>
            <p>
                BOM 文件 <input type="file" name="file" accept=".csv,.txt"/>
                <span class="hint">选中文件时以文件为准（UTF-8 或 GBK 都行）；不选文件就导入下面的文本</span>
            </p>
            <p><textarea name="csv">R0402-10K,10K 0402 电阻,4,R1-R4,N,0.01
C0402-100N,100nF 0402 电容,6,C1-C6,N,0.02
U1-BT5-SOC,主控 SoC,1,U1,Y,18.5
BAT-18650,18650 电池,1,BT1,Y,12</textarea></p>
            <p>
                格式
                <select name="format" class="compact">
                    <option value="">自动识别</option>
                    <option value="MFG">本系统 CSV</option>
                    <option value="FRITZING">Fritzing 导出</option>
                </select>
                <label><input type="checkbox" name="replace" value="N"/> 追加（不勾选则先清空该版本的行）</label>
                <input type="submit" value="导入 BOM"/>
            </p>
            <p class="hint">本系统格式：<code>料号,名称,用量,位号,是否关键料(Y/N),单价(可空)</code>，也可以用 Tab（从 Excel 直接粘）。
                位号里有逗号要加引号，例如 <code>"R1,R2"</code>；料号最多 20 个字符。</p>
            <p class="hint">Fritzing 导出（<code>Label;Part Type;mn;mpn;part number;</code> 加一段 <code>Amount;</code>）可以直接选文件，也可以粘贴：
                用量取 <code>Amount</code> 块、位号取 <code>Label</code> 块合成一行，料号用 <code>part number</code>（没有物料号时用名称）。</p>
        </form>
    </fieldset>

    <h2 id="bomHeading" data-version="${currentVersionId!''}">当前 BOM（<#if currentVersionId?has_content>${currentVersionId}<#else>还没有版本</#if>，${bomItems?size} 行）</h2>
    <#if bomItems?has_content>
        <table id="bomLines">
            <tr><th class="num">行号</th><th>料号</th><th>名称</th><th class="num">用量</th><th>位号</th><th>关键料</th>
                <#if !costDenied><th class="num">单价</th><th class="num">金额</th></#if><th>操作</th></tr>
            <#list bomItems as item>
                <#assign formId = 'bomLine' + item.lineSeq>
                <tr data-line-seq="${item.lineSeq}">
                    <td class="num">${(item.lineSeq)?string("0")}</td>
                    <td>${item.componentCode!}</td>
                    <td>${item.componentName!}</td>
                    <td class="num"><input class="narrow" type="text" form="${formId}" name="qty" value="${(item.qty)?string("0.###")}" title="每台用量"/></td>
                    <td><input type="text" form="${formId}" name="designators" size="12" value="${item.designators!''}"/></td>
                    <td>
                        <select form="${formId}" name="isKeyPart">
                            <option value="N"<#if (item.isKeyPart!'N') != 'Y'> selected="selected"</#if>>N</option>
                            <option value="Y"<#if (item.isKeyPart!'N') == 'Y'> selected="selected"</#if>>Y</option>
                        </select>
                    </td>
                    <#if !costDenied>
                    <td class="num"><input class="narrow" type="text" form="${formId}" name="unitPrice" value="<#if item.unitPrice??>${item.unitPrice?string("0.000")}</#if>" title="采购单价，成本核算用"/></td>
                    <td class="num" data-amount="${((item.qty)!0) * ((item.unitPrice)!0)}"><#if item.unitPrice??>${(((item.qty)!0) * item.unitPrice)?string("0.00")}<#else><span class="hint">—</span></#if></td>
                    </#if>
                    <td class="nowrap">
                        <button type="submit" form="${formId}">保存</button>
                        <button type="submit" form="${formId}" name="delete" value="Y" class="secondary">删除</button>
                    </td>
                </tr>
            </#list>
        </table>
        <#-- the inputs of a row belong to the form of that row; the forms themselves stay empty -->
        <#list bomItems as item>
            <form id="bomLine${item.lineSeq}" method="post" action="/mfg/control/bomItemSave">
                <input type="hidden" name="productCode" value="${product.productCode}"/>
                <input type="hidden" name="bomVersionId" value="${currentVersionId}"/>
                <input type="hidden" name="lineSeq" value="${item.lineSeq}"/>
            </form>
        </#list>
        <fieldset>
            <legend>新增 BOM 行（${currentVersionId!''}）</legend>
            <form method="post" action="/mfg/control/bomItemSave">
                <input type="hidden" name="productCode" value="${product.productCode}"/>
                <input type="hidden" name="bomVersionId" value="${currentVersionId}"/>
                <p>
                    料号 <input type="text" name="componentCode" size="18" required="required"/>
                    名称 <input type="text" name="componentName" size="22"/>
                    用量 <input class="narrow" type="text" name="qty" value="1"/>
                    位号 <input type="text" name="designators" size="12"/>
                    关键料
                    <select name="isKeyPart"><option value="N">N</option><option value="Y">Y</option></select>
                    <#if !costDenied>单价 <input class="narrow" type="text" name="unitPrice"/></#if>
                    <input type="submit" value="添加"/>
                </p>
            </form>
        </fieldset>
    <#else>
        <p class="hint">该型号还没有 BOM，可以用上面的导入框粘贴 / 选文件，或者在下面的表单里一行一行加。</p>
        <fieldset>
            <legend>新增 BOM 行</legend>
            <form method="post" action="/mfg/control/bomItemSave">
                <input type="hidden" name="productCode" value="${product.productCode}"/>
                <p>
                    料号 <input type="text" name="componentCode" size="18" required="required"/>
                    名称 <input type="text" name="componentName" size="22"/>
                    用量 <input class="narrow" type="text" name="qty" value="1"/>
                    位号 <input type="text" name="designators" size="12"/>
                    关键料
                    <select name="isKeyPart"><option value="N">N</option><option value="Y">Y</option></select>
                    <#if !costDenied>单价 <input class="narrow" type="text" name="unitPrice"/></#if>
                    <input type="submit" value="添加"/>
                </p>
            </form>
        </fieldset>
    </#if>

    <h2>成本（单台 / 批量）</h2>
    <#if costDenied>
        <p class="warn">${(c.message)!'无权查看成本与毛利'}</p>
    <#else>
    <form method="get" action="/mfg/control/products" class="inline">
        <input type="hidden" name="productCode" value="${product.productCode}"/>
        批量 <input type="text" name="costQty" size="6" value="${(parameters.costQty)!1}"/>
        <input type="submit" value="重算"/>
    </form>
    <table id="productCost">
        <tr><th>项目</th><th class="num">单台</th><th class="num">合计（${((c.qty)!1)?string("0.##")} 台）</th></tr>
        <tr><td>材料（BOM 单价 × 用量）</td>
            <td class="num">${((c.materialPerUnit)!0)?string("0.00")}</td>
            <td class="num">${((c.materialCost)!0)?string("0.00")}</td></tr>
        <tr><td>工时（<#if (c.hoursSource!'') == 'ROUTING'>按工序分摊 <#else>型号标准工时 </#if>${((c.standardHours)!0)?string("0.##")} h × 费率 ${((c.laborRate)!0)?string("0.00")} 元/h）</td>
            <td class="num">${((c.laborPerUnit)!0)?string("0.00")}</td>
            <td class="num">${((c.laborCost)!0)?string("0.00")}</td></tr>
        <tr><td><b>合计成本</b></td>
            <td class="num"><b>${((c.unitCost)!0)?string("0.00")}</b></td>
            <td class="num"><b>${((c.totalCost)!0)?string("0.00")}</b></td></tr>
        <tr><td>目标成本（开发项目）</td>
            <td class="num"><#if c.targetCost??>${c.targetCost?string("0.00")}<#else><span class="hint">未设</span></#if></td>
            <td class="num"><#if c.targetDifference??><#if (c.targetDifference) gt 0><span class="error">超 ${c.targetDifference?string("0.00")}</span><#else><span class="ok">低于 ${((c.targetDifference)?abs)?string("0.00")}</span></#if><#else><span class="hint">—</span></#if></td></tr>
        <tr><td>目标售价 / 毛利</td>
            <td class="num"><#if c.targetPrice??>${c.targetPrice?string("0.00")}<#else><span class="hint">未设</span></#if></td>
            <td class="num"><#if c.unitMargin??><#if (c.unitMargin) gte 0><span class="ok">毛利 ${c.unitMargin?string("0.00")}（${(c.marginRate!'0')?string("0.0")}%）</span><#else><span class="error">亏 ${((c.unitMargin)?abs)?string("0.00")}</span></#if><#else><span class="hint">—</span></#if></td></tr>
    </table>
    <#if (c.hoursSource!'') == 'ROUTING'>
        <p class="hint">工时按工序模板分摊：${((c.routingHours)!0)?string("0.##")} h/台；型号上的标准工时 ${((c.productStandardHours)!0)?string("0.##")} h 只在没有工序模板时使用。</p>
    <#elseif ((c.productStandardHours)!0) gt 0>
        <p class="hint">该型号还没有工序模板，工时就取型号上的标准工时 ${((c.productStandardHours)!0)?string("0.##")} h；想按工序核算，在下面把工序加全。</p>
    </#if>
    <#if ((c.missingPriceCount)!0) gt 0>
        <p class="warn">有 ${c.missingPriceCount} 行 BOM 没填单价，材料成本被低估；在上面的 BOM 行里补单价（或导入时给第 6 列）。</p>
    </#if>
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

