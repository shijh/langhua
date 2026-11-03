<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
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
<#assign gantt = (d.gantt)!{}>
<#assign ganttMonths = (gantt.months)![]>
<#assign ganttRows = (gantt.rows)![]>
<#assign stageGroups = (d.stageGroups)![]>
<#assign previousProduct = (d.previousProduct)!{}>
<#assign successors = (d.successors)![]>

<#function stageLabel stageId>
    <#if (stageId!'') == 'MFG_STAGE_CONCEPT'><#return '概念'>
    <#elseif (stageId!'') == 'MFG_STAGE_DESIGN'><#return '设计'>
    <#elseif (stageId!'') == 'MFG_STAGE_PROTOTYPE'><#return '打样'>
    <#elseif (stageId!'') == 'MFG_STAGE_TRIAL'><#return '试产'>
    <#elseif (stageId!'') == 'MFG_STAGE_MASS'><#return '量产'>
    <#else><#return '未设置'>
    </#if>
</#function>
<#function reviewLabel resultId>
    <#if (resultId!'') == 'MFG_REVIEW_PASS'><#return '通过'>
    <#elseif (resultId!'') == 'MFG_REVIEW_CONDITIONAL'><#return '有条件通过'>
    <#elseif (resultId!'') == 'MFG_REVIEW_FAIL'><#return '不通过'>
    <#else><#return '未评审'>
    </#if>
</#function>
<#function versionLabel statusId>
    <#if (statusId!'') == 'MFG_BOM_RELEASED'><#return '使用中'>
    <#elseif (statusId!'') == 'MFG_BOM_DRAFT'><#return '草稿'>
    <#elseif (statusId!'') == 'MFG_BOM_OBSOLETE'><#return '已废止'>
    <#else><#return '${statusId!}'>
    </#if>
</#function>
<#function trackLabel trackId>
    <#if (trackId!'') == 'MFG_TRACK_PCB'><#return 'PCB 开发'>
    <#elseif (trackId!'') == 'MFG_TRACK_FIRMWARE'><#return '固件开发'>
    <#elseif (trackId!'') == 'MFG_TRACK_ENCLOSURE'><#return '外壳开发'>
    <#else><#return '未选路线图'>
    </#if>
</#function>
<#function projectStatusLabel statusId>
    <#if (statusId!'') == 'MFG_DEV_ACTIVE'><#return '进行中'>
    <#elseif (statusId!'') == 'MFG_DEV_ONHOLD'><#return '暂停'>
    <#elseif (statusId!'') == 'MFG_DEV_DONE'><#return '已量产'>
    <#elseif (statusId!'') == 'MFG_DEV_CANCELLED'><#return '已取消'>
    <#else><#return '进行中'>
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
<#if parameters.stageId??>
    <p class="ok">已登记 ${stageLabel(parameters.stageId)} 阶段的评审：当前阶段 ${stageLabel((product.devStageId)!'')}。
        往下那段「阶段评审」里能看到每个阶段的结论。</p>
</#if>

<#--
  The board over the model list: how many models there are, how many reached 量产, and what stands in
  the way of the rest (no BOM, nobody working on it, reviews still open). Counted over every model, so
  turning a page or narrowing the search does not move the numbers (mfgProductList.summary).
-->
<#assign sum = (lr.summary)!{}>
<div class="cards" data-summary="products">
    <div class="card" data-card="modelCount">
        <div class="label">型号数</div>
        <div class="value">${((sum.modelCount)!0)?string("0")}</div>
    </div>
    <div class="card" data-card="massCount">
        <div class="label">已量产</div>
        <div class="value">${((sum.massCount)!0)?string("0")}</div>
    </div>
    <div class="card" data-card="noBomCount">
        <div class="label">还没有 BOM</div>
        <div class="value"><#if ((sum.noBomCount)!0) gt 0><a class="warn" href="/mfg/control/products?bom=N" data-card-link="noBomCount" title="列出还没有 BOM 的型号">${(sum.noBomCount)?string("0")}</a><#else>0</#if></div>
    </div>
    <div class="card" data-card="noProjectCount">
        <div class="label">没有开发项目</div>
        <div class="value"><#if ((sum.noProjectCount)!0) gt 0><a class="warn" href="/mfg/control/products?project=N" data-card-link="noProjectCount" title="列出没有任何开发项目的型号">${(sum.noProjectCount)?string("0")}</a><#else>0</#if></div>
    </div>
    <div class="card" data-card="gatePassed">
        <div class="label">阶段评审通过 / 已评审</div>
        <div class="value">${((sum.gatePassed)!0)?string("0")} / ${((sum.gateReviewed)!0)?string("0")}</div>
    </div>
    <div class="card" data-card="gatePending">
        <div class="label">还待评审的阶段</div>
        <div class="value">${((sum.gatePending)!0)?string("0")}<span class="hint">（每个型号 5 道）</span></div>
    </div>
</div>

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
            上代产品 <input type="text" name="previousProductCode" size="10" value="${(product.previousProductCode)!''}" placeholder="被它替代的型号"/>
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
    BOM <select name="bom" data-filter="bom">
        <option value="">全部</option>
        <option value="Y"<#if (lr.bom!'') == 'Y'> selected="selected"</#if>>已有</option>
        <option value="N"<#if (lr.bom!'') == 'N'> selected="selected"</#if>>还没有</option>
    </select>
    开发项目 <select name="project" data-filter="project">
        <option value="">全部</option>
        <option value="Y"<#if (lr.project!'') == 'Y'> selected="selected"</#if>>有</option>
        <option value="N"<#if (lr.project!'') == 'N'> selected="selected"</#if>>没有</option>
    </select>
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
            <th class="num">阶段评审</th>
            <th>当前 BOM</th>
            <th class="num">BOM 行</th>
            <th class="num">版本数</th>
            <th class="num">照片</th>
            <th class="num">工序数</th>
            <th>状态</th>
        </tr>
        <#list products as p>
            <#assign bomLines = (p.bomItems)![]>
            <tr>
                <td><#if p.latestImageSeq??><img src="/mfg/control/productImage?productCode=${p.productCode}&seq=${p.latestImageSeq}&thumb=1" alt="${p.productCode}" width="44" height="44" style="object-fit: cover; border: 1px solid #3c3c3c;"/><#else><span class="hint">-</span></#if></td>
                <td><a href="/mfg/control/products?productCode=${p.productCode}">${p.productCode}</a></td>
                <td>${p.productName!}</td>
                <td>${p.snPrefix!}</td>
                <td>${stageLabel(p.devStageId!'')}</td>
                <td class="num">${(p.passedCount)!0} / ${(p.reviewCount)!0}</td>
                <td><#if (p.currentBomVersionId!'') != ''>${p.currentBomVersionId}<#else><span class="hint">未建 BOM</span></#if></td>
                <td class="num"><#if bomLines?has_content><a href="#" data-toggle-lines="pc-${p.productCode}" data-open="N">${bomLines?size} <span>▸</span></a><#else>0</#if></td>
                <td class="num">${p.versionCount}</td>
                <td class="num">${p.imageCount}</td>
                <td class="num">${p.routingCount}</td>
                <td>${p.statusId!}</td>
            </tr>
            <#if bomLines?has_content>
                <tr data-lines="pc-${p.productCode}"><td colspan="12" class="lines">
                    <table class="inner" data-product-bom="${p.productCode}">
                        <tr><th>行</th><th>料号</th><th>名称</th><th class="num">用量</th>
                            <th class="num">单价</th><th class="num">金额</th><th>位号</th><th>关键件</th></tr>
                        <#list bomLines as item>
                            <tr data-line-seq="${item.lineSeq}">
                                <td>${item.lineSeq}</td>
                                <td>${item.componentCode!}</td>
                                <td>${item.componentName!}</td>
                                <td class="num">${(item.qty!0)?string("0.##")}</td>
                                <td class="num"><#if item.unitPrice??>${item.unitPrice?string("0.0000")}<#else><span class="hint">-</span></#if></td>
                                <td class="num"><#if item.unitPrice??>${((item.unitPrice) * (item.qty!0))?string("0.00")}<#else><span class="hint">-</span></#if></td>
                                <td>${item.designators!}</td>
                                <td><#if (item.isKeyPart!'') == 'Y'><span class="warn">关键</span></#if></td>
                            </tr>
                        </#list>
                    </table>
                    <p class="hint">这是当前版本的 BOM；要改行、切版本或看历史，点上面的型号进详情页。</p>
                </td></tr>
            </#if>
        </#list>
    </table>
<#else>
    <p class="hint">还没有产品型号，先在上面的表单里创建一个（例如 <code>BT5</code>）。</p>
</#if>
<@pg.pager action="/mfg/control/products" query=('search=' + ((lr.search)!'')?url + '&' + 'bom=' + ((lr.bom)!'')?url + '&' + 'project=' + ((lr.project)!'')?url)
    page=(lr.page)!1 pageCount=(lr.pageCount)!1 total=(lr.totalCount)!0 unit="条" pageSize=(lr.pageSize)!20/>

<#if product.productCode??>
    <h2 id="stages" data-stage="${(product.devStageId)!''}">阶段评审（已通过 ${(d.passedCount)!0} / 已评审 ${(d.reviewCount)!0}）</h2>
    <#assign stageRows = (d.reviews)![]>
    <table id="stageGates">
        <tr><th>阶段</th><th>计划评审</th><th>实际评审</th><th>结论</th><th>评审人</th><th>说明</th></tr>
        <#list stageRows as r>
            <tr data-stage="${r.stageId}">
                <td><#if (product.devStageId!'') == r.stageId><b>${stageLabel(r.stageId)}</b><#else>${stageLabel(r.stageId)}</#if></td>
                <td><#if r.plannedDate??>${r.plannedDate?string("yyyy-MM-dd")}</#if></td>
                <td><#if r.doneDate??>${r.doneDate?string("yyyy-MM-dd")}</#if></td>
                <td><#if r.resultId??>${reviewLabel(r.resultId)}<#else><span class="hint">未评审</span></#if></td>
                <td>${r.reviewer!}</td>
                <td>${r.note!}</td>
            </tr>
        </#list>
    </table>

    <fieldset>
        <legend>登记阶段评审（通过则进入下一阶段）</legend>
        <form method="post" action="/mfg/control/productStageSave">
            <input type="hidden" name="productCode" value="${product.productCode}"/>
            <p>
                阶段
                <select name="stageId">
                    <#list stageRows as r>
                        <option value="${r.stageId}"<#if (product.devStageId!'') == r.stageId> selected="selected"</#if>>${stageLabel(r.stageId)}</option>
                    </#list>
                </select>
                结论
                <select name="resultId">
                    <option value="MFG_REVIEW_PASS">通过</option>
                    <option value="MFG_REVIEW_CONDITIONAL">有条件通过</option>
                    <option value="MFG_REVIEW_FAIL">不通过</option>
                </select>
                计划评审 <input type="text" name="plannedDate" size="10" placeholder="yyyy-mm-dd"/>
                评审日 <input type="text" name="doneDate" size="10" placeholder="yyyy-mm-dd（留空=今天）"/>
                评审人 <input type="text" name="reviewer" size="10"/>
            </p>
            <p>
                说明 <input type="text" name="note" size="46"/>
                <input type="submit" value="保存评审"/>
            </p>
        </form>
    </fieldset>
    <p class="hint">五个阶段挂在型号上：同一个型号的 PCB、固件、外壳三个项目共用这套阶段评审，评审通过才进入下一阶段；
        项目自己在立项时选「在做哪一步」，只是指向这里的某一个评审节点，不再有自己的评审表。</p>

    <#-- the chart is about one product, so the product is named right above its title, in the same
         size as that title and resting on its rule; the generation links sit on the same line -->
    <p class="gantt-product" data-gantt-product="${product.productCode}">
        <span class="hint">产品</span> <b>${product.productCode}</b><#if (product.productName!'') != ''> ${product.productName}</#if>
        <#if previousProduct?has_content>
            <span class="gantt-generation" data-gantt-previous="${previousProduct.productCode}">上代产品
                <a href="/mfg/control/products?productCode=${previousProduct.productCode}#projects">${previousProduct.productCode}</a> ${previousProduct.productName!''}</span>
        </#if>
        <#if successors?has_content>
            <span class="gantt-generation" data-gantt-successors="${successors?size}">升级替代
                <#list successors as n><a href="/mfg/control/products?productCode=${n.productCode}#projects">${n.productCode}</a> ${n.productName!''}<#if n_has_next>、</#if></#list></span>
        </#if>
    </p>
    <h2 id="projects">阶段与项目（甘特图）</h2>
    <#if stageGroups?has_content>
        <#if ganttRows?has_content>
            <div class="gantt" data-gantt="product" data-gantt-rows="${ganttRows?size}">
                <div class="gantt-head">
                    <div class="gantt-name">阶段 / 项目</div>
                    <div class="gantt-track">
                        <#list ganttMonths as m>
                            <span class="gantt-month" style="left:${m.left}%; width:${m.width}%">${m.label}</span>
                        </#list>
                        <i class="gantt-today" style="left:${gantt.todayLeft}%" title="今天 ${gantt.todayText}"></i>
                    </div>
                </div>
                <#list ganttRows as r>
                    <div class="gantt-row<#if r.kind == 'gate'> gantt-gate<#else> gantt-project</#if>" data-gantt-row="${r.kind}" data-gantt-tone="${r.tone}"<#if (r.stageId!'') != ''> data-gantt-stage="${r.stageId}"</#if>>
                        <div class="gantt-name">
                            <#if r.kind == 'gate'>
                                <span class="gantt-kind ${r.tone}">评审</span>${stageLabel(r.stageId!'')}
                            <#else>
                                <#if r.tone == 'cancelled'><span class="gantt-cross" title="项目已取消">✕</span></#if>
                                <a href="/mfg/control/dev?projectCode=${r.projectCode}#project">${r.projectName!r.projectCode}</a>
                                <span class="hint">${trackLabel(r.trackId!'')}<#if (r.stageId!'') != ''> · ${stageLabel(r.stageId)}</#if></span>
                            </#if>
                        </div>
                        <div class="gantt-track">
                            <#list ganttMonths as m><i class="gantt-grid" style="left:${m.left}%"></i></#list>
                            <i class="gantt-today" style="left:${gantt.todayLeft}%"></i>
                            <#if r.noDates>
                                <span class="gantt-none">未排期</span>
                            <#else>
                                <span class="gantt-bar ${r.tone}" style="left:${r.left}%; width:${r.width}%"
                                      title="${r.startText} → ${r.endText}"><#if r.labelInside>${r.caption}</#if></span>
                                <#if !r.labelInside>
                                    <span class="gantt-tag ${r.tagSide}" style="left:${r.tagLeft}%">${r.caption}</span>
                                </#if>
                            </#if>
                        </div>
                    </div>
                </#list>
            </div>
            <p class="gantt-legend">
                <span class="legend-label">评审结论</span>
                <span><span class="chip done"></span>评审通过</span>
                <span><span class="chip conditional"></span>有条件通过</span>
                <span><span class="chip fail"></span>不通过</span>
                <span><span class="chip plan"></span>待评审</span>
                <span><span class="chip none"></span>未排期</span>
            </p>
            <p class="gantt-legend">
                <span class="legend-label">项目状态</span>
                <span><span class="chip live"></span>进行中</span>
                <span><span class="chip launched"></span>已量产</span>
                <span><span class="chip cancelled"></span><span class="gantt-cross">✕</span>已取消</span>
                <span><span class="chip hold"></span>暂停</span>
            </p>
            <p class="hint">阶段评审那条从「计划评审日」画到「实际评审日」（还没评审的画到今天）；项目那条从「立项」画到
                「实际上市日」，没填实际上市就看计划上市日。竖线是今天。</p>
        <#else>
            <p class="hint">这个型号的阶段评审和项目都还没有日期，排不出甘特图。先在产品上面登记阶段评审的日期，
                或者在项目里填计划上市日。</p>
        </#if>

        <table id="productProjects">
            <tr>
                <th>阶段</th><th>项目</th><th>路线图</th><th>负责人</th>
                <th>立项</th><th>计划上市</th><th>实际上市</th><th>状态</th>
            </tr>
            <#list stageGroups as grp>
                <#list grp.projects as p>
                    <tr data-project-stage="${grp.stageId}" data-project-code="${p.projectCode}">
                        <td><#if ((grp.stageId)!'') == ''><span class="hint">未定阶段</span><#else>${stageLabel(grp.stageId)}</#if></td>
                        <td><a href="/mfg/control/dev?projectCode=${p.projectCode}#project">${p.projectName!p.projectCode}</a>
                            <span class="hint">${p.projectCode}</span></td>
                        <td>${trackLabel(p.trackId!'')}</td>
                        <td>${p.ownerName!}</td>
                        <td><#if p.startDate??>${p.startDate?string("yyyy-MM-dd")}</#if></td>
                        <td><#if p.plannedLaunchDate??>${p.plannedLaunchDate?string("yyyy-MM-dd")}</#if></td>
                        <td><#if p.actualLaunchDate??>${p.actualLaunchDate?string("yyyy-MM-dd")}</#if></td>
                        <td>${projectStatusLabel(p.statusId!'')}</td>
                    </tr>
                </#list>
            </#list>
        </table>
        <p class="hint">一个项目通过两个字段挂到这个型号上：<b>型号</b>（哪个产品）与<b>阶段</b>（在做产品的哪一步）。
            改阶段在「项目开发」页的立项表单里。</p>
    <#else>
        <p class="hint">还没有项目挂到这个型号上。在「项目开发」页立项时把「型号」选成 ${product.productCode}，
            它就会出现在这里。</p>
    </#if>

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
                                <#-- the BOM block is below the product form: come back to it (改完停在原地) -->
                                <input type="hidden" name="backTo" value="bomBlock"/>
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
                    <option value="FRITZING_HTML">Fritzing HTML（bom.html）</option>
                </select>
                <label><input type="checkbox" name="replace" value="N"/> 追加（不勾选则先清空该版本的行）</label>
                <input type="submit" value="导入 BOM"/>
            </p>
            <p class="hint">本系统格式：<code>料号,名称,用量,位号,是否关键料(Y/N),单价(可空)</code>，也可以用 Tab（从 Excel 直接粘）。
                位号里有逗号要加引号，例如 <code>"R1,R2"</code>；料号最多 20 个字符。</p>
            <p class="hint">Fritzing 导出（<code>Label;Part Type;mn;mpn;part number;</code> 加一段 <code>Amount;</code>）可以直接选文件，也可以粘贴：
                用量取 <code>Amount</code> 块、位号取 <code>Label</code> 块合成一行，料号用 <code>part number</code>（没有物料号时用名称）。</p>
            <p class="hint">Fritzing 的 <code>bom.html</code>（导出 → 作为图片 → 物料清单）也能直接选：料号取 MPN / MN / <code>元件号</code>，
                都没有就按型号与参数拼（如 <code>R-10K-0603</code>），导入后可以逐行改。</p>
        </form>
    </fieldset>

    <#-- the block is replaced in place after a save, so the page keeps its scroll position -->
    <div id="bomBlock">
    <h2 id="bomHeading" data-version="${currentVersionId!''}">当前 BOM（<#if currentVersionId?has_content>${currentVersionId}<#else>还没有版本</#if>，${bomItems?size} 行）</h2>
    <#if bomItems?has_content>
        <table id="bomLines">
            <tr><th class="num">行号</th><th>料号</th><th>名称</th><th class="num">用量</th><th>位号</th><th>关键料</th>
                <#if !costDenied><th class="num">单价</th><th class="num">金额</th></#if><th>操作</th></tr>
            <#list bomItems as item>
                <#assign formId = 'bomLine' + item.lineSeq>
                <tr data-line-seq="${item.lineSeq}">
                    <td class="num">${(item.lineSeq)?string("0")}</td>
                    <td>${item.componentCode!}<#if (item.scanShotId!'')?has_content>
                        <#-- the shop's own photo of this part, when it came in from an order screenshot -->
                        <img class="shot" data-part-shot="${item.scanShotId}" alt=""
                             src="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${item.scanShotId?url}&amp;thumb=1"/></#if></td>
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
                    名称 <input type="text" name="partName" size="22"/>
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
                    名称 <input type="text" name="partName" size="22"/>
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
    </div>
    <p id="bomToast" class="ok" hidden="hidden"></p>

    <script>
    /* Saving a BOM line must not throw the page away: post it, then refresh only #bomBlock.
       Without JavaScript the same buttons still work as plain form posts. */
    (function () {
        var block = document.getElementById('bomBlock');
        var toast = document.getElementById('bomToast');
        if (!block || !toast || !window.fetch || !window.URLSearchParams || !window.DOMParser) {
            return;
        }
        function say(text, good) {
            toast.className = good ? 'ok' : 'error';
            toast.textContent = text;
            toast.removeAttribute('hidden');
        }
        function refreshBlock(message) {
            return fetch(location.href, { credentials: 'same-origin' }).then(function (answer) {
                return answer.text();
            }).then(function (html) {
                var fresh = new DOMParser().parseFromString(html, 'text/html').getElementById('bomBlock');
                if (!fresh) {
                    location.reload();
                    return;
                }
                block.innerHTML = fresh.innerHTML;
                say(message, true);
            });
        }
        document.addEventListener('submit', function (event) {
            var form = event.target;
            var action = form && form.getAttribute('action');
            if (!action || action.indexOf('/mfg/control/bomItemSave') !== 0) {
                return;
            }
            event.preventDefault();
            fetch('/mfg/control/bomItemSaveJson', {
                method: 'POST',
                body: new URLSearchParams(new FormData(form)),
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }
            }).then(jsonOrLogin).then(function (data) {
                if (!data.ok) {
                    say(data.message || '保存失败', false);
                    return;
                }
                return refreshBlock(data.message);
            }).catch(function (problem) {
                if (problem && problem.message === 'login') {
                    return;
                }
                form.submit();
            });
        });
    }());
    </script>

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

