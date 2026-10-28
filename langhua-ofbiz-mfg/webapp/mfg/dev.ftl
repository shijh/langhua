<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  Product development: project list, the track steps, the issue list, the stage ladder and the
  gate reviews.
  Data: listResult.projects / stages / stageId / statusId, productList.products,
        detail.project / detail.reviews / detail.ecos / detail.steps / detail.trackId,
        issueResult.issues / issueResult.openCount / issueResult.highOpenCount
-->
<#assign lr = (listResult)!{}>
<#assign projects = (lr.projects)![]>
<#assign stages = (lr.stages)![]>
<#assign pl = (productList)!{}>
<#assign products = (pl.products)![]>
<#assign d = (detail)!{}>
<#assign project = (d.project)!{}>
<#assign reviews = (d.reviews)![]>
<#assign ecos = (d.ecos)![]>
<#assign steps = (d.steps)![]>
<#assign trackId = (d.trackId)!''>
<#assign atts = ((attachmentResult).attachments)![]>
<#assign issues = ((issueResult).issues)![]>
<#assign issueOpen = ((issueResult).openCount)!0>
<#assign issueHigh = ((issueResult).highOpenCount)!0>
<#assign bom = (bomResult)!{}>
<#assign bomLatest = (bom.latest)!{}>
<#assign bomItems = (bom.items)![]>
<#assign bomDropped = (bom.dropped)![]>
<#assign bomShortages = (bom.shortages)![]>
<#assign bomImports = (bom.imports)![]>
<#assign bomShortCount = (bom.shortCount)!0>
<#assign bomShortQty = (bom.shortQty)!0>
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
<#function statusLabel statusId>
    <#if (statusId!'') == 'MFG_DEV_ACTIVE'><#return '进行中'>
    <#elseif (statusId!'') == 'MFG_DEV_ONHOLD'><#return '暂停'>
    <#elseif (statusId!'') == 'MFG_DEV_DONE'><#return '已量产'>
    <#elseif (statusId!'') == 'MFG_DEV_CANCELLED'><#return '已取消'>
    <#else><#return '${statusId!}'>
    </#if>
</#function>
<#function reviewLabel resultId>
    <#if (resultId!'') == 'MFG_REVIEW_PASS'><#return '通过'>
    <#elseif (resultId!'') == 'MFG_REVIEW_CONDITIONAL'><#return '有条件通过'>
    <#elseif (resultId!'') == 'MFG_REVIEW_FAIL'><#return '不通过'>
    <#else><#return '未评审'>
    </#if>
</#function>
<#function trackLabel trackId>
    <#if (trackId!'') == 'MFG_TRACK_PCB'><#return 'PCB 开发'>
    <#elseif (trackId!'') == 'MFG_TRACK_FIRMWARE'><#return '固件开发'>
    <#elseif (trackId!'') == 'MFG_TRACK_ENCLOSURE'><#return '外壳开发'>
    <#else><#return '仅阶段管理'>
    </#if>
</#function>
<#function stepLabel stepId>
    <#if (stepId!'') == 'MFG_PCB_BREADBOARD'><#return '面包板验证'>
    <#elseif (stepId!'') == 'MFG_PCB_SCHEMATIC'><#return '原理图'>
    <#elseif (stepId!'') == 'MFG_PCB_LAYOUT'><#return 'PCB 布线'>
    <#elseif (stepId!'') == 'MFG_PCB_SAMPLE'><#return '样板打样'>
    <#elseif (stepId!'') == 'MFG_PCB_HAND_SOLDER'><#return '手焊测试'>
    <#elseif (stepId!'') == 'MFG_FW_DEVELOP'><#return '固件开发'>
    <#elseif (stepId!'') == 'MFG_FW_FLASH'><#return '烧录'>
    <#elseif (stepId!'') == 'MFG_FW_FUNCTION_TEST'><#return '功能测试'>
    <#elseif (stepId!'') == 'MFG_ENC_BLENDER'><#return 'Gerber/PNP 导入 Blender 设计'>
    <#elseif (stepId!'') == 'MFG_ENC_PRINT_CHECK'><#return '3D 打印与装配确认'>
    <#elseif (stepId!'') == 'MFG_ENC_CONTRACT'><#return '注塑厂家签合同'>
    <#elseif (stepId!'') == 'MFG_ENC_PAY'><#return '付款'>
    <#elseif (stepId!'') == 'MFG_ENC_PRODUCTION'><#return '生产外壳'>
    <#elseif (stepId!'') == 'MFG_ENC_STOCK_IN'><#return '外壳入库'>
    <#elseif (stepId!'') == 'MFG_ENC_ASSEMBLY'><#return '产品组装'>
    <#else><#return '${stepId!}'>
    </#if>
</#function>
<#function stepStatusLabel statusId>
    <#if (statusId!'') == 'MFG_STEP_DONE'><#return '已完成'>
    <#elseif (statusId!'') == 'MFG_STEP_DOING'><#return '进行中'>
    <#elseif (statusId!'') == 'MFG_STEP_SKIPPED'><#return '跳过'>
    <#else><#return '未开始'>
    </#if>
</#function>
<#function issueSeverityLabel severityId>
    <#if (severityId!'') == 'MFG_ISSUE_HIGH'><#return '严重'>
    <#elseif (severityId!'') == 'MFG_ISSUE_MEDIUM'><#return '一般'>
    <#elseif (severityId!'') == 'MFG_ISSUE_LOW'><#return '轻微'>
    <#else><#return '一般'>
    </#if>
</#function>
<#function issueStatusLabel statusId>
    <#if (statusId!'') == 'MFG_ISSUE_CLOSED'><#return '已关闭'>
    <#elseif (statusId!'') == 'MFG_ISSUE_DOING'><#return '处理中'>
    <#else><#return '未处理'>
    </#if>
</#function>
<#function bomChangeLabel changeId>
    <#if (changeId!'') == 'MFG_BOM_NEW'><#return '新增'>
    <#elseif (changeId!'') == 'MFG_BOM_CHANGED'><#return '数量变化'>
    <#elseif (changeId!'') == 'MFG_BOM_DROPPED'><#return '不再使用'>
    <#else><#return '不变'>
    </#if>
</#function>

<h1>产品开发</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>立项 / 修改项目</legend>
    <form method="post" action="/mfg/control/devProjectSave">
        <input type="hidden" name="projectCode" value="${project.projectCode!''}"/>
        <p>
            项目名称 <input type="text" name="projectName" size="22" value="${project.projectName!''}"/>
            线路
            <select name="trackId">
                <option value=""<#if (project.trackId!'') == ''> selected="selected"</#if>>仅阶段管理</option>
                <option value="MFG_TRACK_PCB"<#if (project.trackId!'') == 'MFG_TRACK_PCB'> selected="selected"</#if>>PCB 开发</option>
                <option value="MFG_TRACK_FIRMWARE"<#if (project.trackId!'') == 'MFG_TRACK_FIRMWARE'> selected="selected"</#if>>固件开发</option>
                <option value="MFG_TRACK_ENCLOSURE"<#if (project.trackId!'') == 'MFG_TRACK_ENCLOSURE'> selected="selected"</#if>>外壳开发</option>
            </select>
            料号 <input type="text" name="partCode" size="10" value="${project.partCode!''}" placeholder="板号/外壳料号"/>
            型号
            <select name="productCode">
                <option value="">（未定）</option>
                <#list products as p>
                    <option value="${p.productCode}"<#if (project.productCode!'') == p.productCode> selected="selected"</#if>>${p.productCode}</option>
                </#list>
            </select>
        </p>
        <p>
            阶段
            <select name="stageId">
                <#list stages as s>
                    <option value="${s.stageId}"<#if (project.stageId!'') == s.stageId> selected="selected"</#if>>${stageLabel(s.stageId)}</option>
                </#list>
            </select>
            责任人 <input type="text" name="ownerName" size="9" value="${project.ownerName!''}"/>
            目标成本 <input type="text" name="targetCost" size="8" value="${project.targetCost!0}"/>
            目标售价 <input type="text" name="targetPrice" size="8" value="${project.targetPrice!0}"/>
            计划上市 <input type="text" name="plannedLaunchDate" size="10" placeholder="yyyy-mm-dd" value="<#if project.plannedLaunchDate??>${project.plannedLaunchDate?string("yyyy-MM-dd")}</#if>"/>
        </p>
        <p>
            备注 <input type="text" name="note" size="46" value="${project.note!''}"/>
            <input type="submit" value="保存项目"/>
        </p>
    </form>
</fieldset>

<h2>项目列表</h2>
<form method="get" action="/mfg/control/dev">
    搜索 <input type="text" name="search" size="20" value="${(lr.search)!''}" placeholder="项目号 / 名称 / 型号 / 责任人"/>
    阶段
    <select name="stageId">
        <option value="">全部</option>
        <#list stages as s>
            <option value="${s.stageId}"<#if (lr.stageId!'') == s.stageId> selected="selected"</#if>>${stageLabel(s.stageId)}</option>
        </#list>
    </select>
    状态
    <select name="statusId">
        <option value="">全部</option>
        <option value="MFG_DEV_ACTIVE"<#if (lr.statusId!'') == 'MFG_DEV_ACTIVE'> selected="selected"</#if>>进行中</option>
        <option value="MFG_DEV_ONHOLD"<#if (lr.statusId!'') == 'MFG_DEV_ONHOLD'> selected="selected"</#if>>暂停</option>
        <option value="MFG_DEV_DONE"<#if (lr.statusId!'') == 'MFG_DEV_DONE'> selected="selected"</#if>>已量产</option>
        <option value="MFG_DEV_CANCELLED"<#if (lr.statusId!'') == 'MFG_DEV_CANCELLED'> selected="selected"</#if>>已取消</option>
    </select>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(lr.totalCount)!0} 条</span>
</form>

<#if projects?has_content>
    <table>
        <tr>
            <th>项目号</th><th>线路</th><th>名称</th><th>料号</th><th>型号</th>
            <th>步骤</th><th>当前步骤</th><th>阶段</th>
            <th class="num">关卡</th><th class="num">未结变更</th><th class="num">未结问题</th><th>状态</th>
        </tr>
        <#list projects as p>
            <tr>
                <td><a href="/mfg/control/dev?projectCode=${p.projectCode}">${p.projectCode}</a></td>
                <td>${trackLabel(p.trackId!'')}</td>
                <td>${p.projectName!}</td>
                <td>${p.partCode!}</td>
                <td>${p.productCode!}</td>
                <td class="num"><#if (p.stepTotal!0) gt 0>${p.stepDone} / ${p.stepTotal}<#else>-</#if></td>
                <td><#if p.currentStepId??>${stepLabel(p.currentStepId)}<#else><span class="ok">已走完</span></#if></td>
                <td>${stageLabel(p.stageId!'')}</td>
                <td class="num">${p.passedCount} / ${p.reviewCount}</td>
                <td class="num"><#if (p.openEcoCount!0) gt 0><span class="warn">${p.openEcoCount}</span><#else>0</#if></td>
                <td class="num"><#if (p.openIssueCount!0) gt 0><a class="warn" href="/mfg/control/dev?projectCode=${p.projectCode}#issues">${p.openIssueCount}</a><#else>0</#if></td>
                <td>${statusLabel(p.statusId!'')}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有开发项目，先在上面立项。三条线（PCB / 固件 / 外壳）各建一个项目，步骤清单由线路决定。</p>
</#if>
<@pg.pager action="/mfg/control/dev" query=('search=' + ((lr.search)!'')?url + '&' + 'stageId=' + ((lr.stageId)!'')?url + '&' + 'statusId=' + ((lr.statusId)!'')?url)
    page=(lr.page)!1 pageCount=(lr.pageCount)!1 total=(lr.totalCount)!0 unit="条" pageSize=(lr.pageSize)!20/>

<#if project.projectCode??>
    <h2>${project.projectCode} ${project.projectName!}</h2>
    <ul>
        <li>线路：<b>${trackLabel(project.trackId!'')}</b><#if (project.partCode!'') != ''>，料号 ${project.partCode}</#if><#if (project.stepTotal!0) gt 0>，步骤 ${project.stepDone} / ${project.stepTotal}</#if></li>
        <li>型号：<#if (project.productCode!'') != ''>${project.productCode}<#else><span class="hint">未关联</span></#if></li>
        <li>当前阶段：<b>${stageLabel(project.stageId!'')}</b><#if project.nextStageId??> → 下一关：${stageLabel(project.nextStageId)}</#if></li>
        <li>目标成本 / 售价：${project.targetCost!0} / ${project.targetPrice!0}
            <#if (project.targetCost!0) gt 0><span class="hint">毛利 ${((project.targetPrice!0) - (project.targetCost!0))?string("0.00")}</span></#if>
        </li>
        <li>计划上市：<#if project.plannedLaunchDate??>${project.plannedLaunchDate?string("yyyy-MM-dd")}<#else>-</#if>
            实际上市：<#if project.actualLaunchDate??>${project.actualLaunchDate?string("yyyy-MM-dd")}<#else>-</#if></li>
    </ul>

    <#if steps?has_content>
        <h3>${trackLabel(trackId)} 步骤</h3>
        <table>
            <tr>
                <th class="num">#</th><th>步骤</th><th>状态</th><th>计划</th><th>完成</th>
                <th>负责人</th><th>供应商</th><th class="num">金额</th><th class="num">数量</th>
                <th class="num">问题</th><th>单号</th><th>备注</th><th>操作</th>
            </tr>
            <#list steps as s>
                <tr>
                    <td class="num">${s.stepIndex + 1}</td>
                    <td><#if s.statusId == 'MFG_STEP_DONE'><span class="ok">${stepLabel(s.stepId)}</span><#else>${stepLabel(s.stepId)}</#if></td>
                    <td>${stepStatusLabel(s.statusId!'')}</td>
                    <td><#if s.plannedDate??>${s.plannedDate?string("MM-dd")}</#if></td>
                    <td><#if s.doneDate??>${s.doneDate?string("MM-dd")}</#if></td>
                    <td>${s.ownerName!}</td>
                    <td>${s.vendorName!}</td>
                    <td class="num"><#if (s.amount!0) gt 0>${(s.amount)?string("0.00")}</#if></td>
                    <td class="num"><#if (s.qty!0) gt 0>${(s.qty)?string("0.##")}</#if></td>
                    <td class="num"><#if (s.openIssueCount!0) gt 0><a class="warn" href="#issues">${s.openIssueCount}</a></#if></td>
                    <td>${s.referenceNo!}<#if s.lotId??><br/><span class="hint">批次 ${s.lotId}</span></#if></td>
                    <td>${s.note!}</td>
                    <td>
                        <#if s.statusId != 'MFG_STEP_DOING' && s.statusId != 'MFG_STEP_DONE'>
                            <form method="post" action="/mfg/control/devStepStatus" class="inline">
                                <input type="hidden" name="projectCode" value="${project.projectCode}"/>
                                <input type="hidden" name="stepId" value="${s.stepId}"/>
                                <input type="hidden" name="statusId" value="MFG_STEP_DOING"/>
                                <input type="submit" value="开始"/>
                            </form>
                        </#if>
                        <#if s.statusId != 'MFG_STEP_DONE'>
                            <form method="post" action="/mfg/control/devStepStatus" class="inline">
                                <input type="hidden" name="projectCode" value="${project.projectCode}"/>
                                <input type="hidden" name="stepId" value="${s.stepId}"/>
                                <input type="hidden" name="statusId" value="MFG_STEP_DONE"/>
                                <input type="submit" value="完成"/>
                            </form>
                        </#if>
                    </td>
                </tr>
            </#list>
        </table>

        <fieldset>
            <legend>更新步骤详情（留空的字段会被清空）</legend>
            <form method="post" action="/mfg/control/devStepSave">
                <input type="hidden" name="projectCode" value="${project.projectCode}"/>
                <p>
                    步骤
                    <select name="stepId">
                        <#list steps as s>
                            <option value="${s.stepId}">${stepLabel(s.stepId)}</option>
                        </#list>
                    </select>
                    状态
                    <select name="statusId">
                        <option value="MFG_STEP_TODO">未开始</option>
                        <option value="MFG_STEP_DOING">进行中</option>
                        <option value="MFG_STEP_DONE">已完成</option>
                        <option value="MFG_STEP_SKIPPED">跳过</option>
                    </select>
                    计划日 <input type="text" name="plannedDate" size="9" placeholder="yyyy-mm-dd"/>
                    完成日 <input type="text" name="doneDate" size="9" placeholder="yyyy-mm-dd"/>
                </p>
                <p>
                    负责人 <input type="text" name="ownerName" size="9"/>
                    供应商 <input type="text" name="vendorName" size="12"/>
                    金额 <input type="text" name="amount" size="7"/>
                    数量 <input type="text" name="qty" size="6"/>
                    单号 <input type="text" name="referenceNo" size="14" placeholder="合同号/付款凭证/快递单"/>
                    备注 <input type="text" name="note" size="24"/>
                    <input type="submit" value="保存步骤"/>
                </p>
            </form>
        </fieldset>
        <p class="hint">外壳开发线把「外壳入库」置为已完成时，会按项目的料号和本步骤填的数量自动生成一个物料批次；组装时按正常流程开工单。</p>
    </#if>

    <h3 id="devbom">研发 BOM（Fritzing）与缺货外购清单</h3>
    <#if parameters.bomVersion??>
        <p class="ok">已导入 ${parameters.bomVersion}：${(parameters.bomItemCount)!0} 个元件（新增 ${(parameters.bomNewCount)!0} / 数量变化 ${(parameters.bomChangedCount)!0} / 不再使用 ${(parameters.bomDroppedCount)!0}），锁料 ${(parameters.bomLockedQty)!'0'} 件，缺货 ${(parameters.bomShortCount)!0} 项<#if (parameters.bomConsumedQty!'0') != '0'>，记作研发消耗 ${parameters.bomConsumedQty} 件</#if></p>
    </#if>
    <#if parameters.releasedQty??>
        <p class="ok">已释放本项目的锁料 ${parameters.releasedQty} 件（库存数量没有变）</p>
    </#if>
    <#if parameters.componentCode??>
        <p class="ok">第 ${(parameters.lineSeq)!''} 行已按料号 ${parameters.componentCode} 重新锁料：已锁 ${(parameters.lockedQty)!'0'}，缺 ${(parameters.shortQty)!'0'}</p>
    </#if>

    <#if bomLatest?has_content>
        <p class="hint">
            当前版本 <b>${bomLatest.bomVersion!}</b>（第 ${bomLatest.importSeq} 次导入<#if bomLatest.sourceFile??>，来自 ${bomLatest.sourceFile}</#if><#if bomLatest.importedStamp??>，${bomLatest.importedStamp?string("yyyy-MM-dd HH:mm")}</#if>）：
            ${(bomLatest.itemCount)!0} 个元件，缺货 <b>${bomShortCount}</b> 项 / ${bomShortQty?string("0.##")} 件。
            导入时这一版的记录：${bomLatest.note!''}
        </p>
    </#if>

    <#if bomShortages?has_content>
        <h4>缺货外购清单（${bomShortCount} 项 / ${bomShortQty?string("0.##")} 件）
            <a href="/mfg/control/devBomCsv?projectCode=${project.projectCode}">下载 CSV</a></h4>
        <table id="devShortageTable">
            <tr>
                <th>料号</th><th>名称</th><th class="num">需求</th><th class="num">已锁</th>
                <th class="num">在库</th><th class="num">缺口</th><th class="num">建议采购</th><th>位号</th>
            </tr>
            <#list bomShortages as r>
                <tr data-bom-short="${r.componentCode}">
                    <td>${r.componentCode}</td>
                    <td>${r.componentName!}<#if r.properties??><br/><span class="hint">${r.properties}</span></#if></td>
                    <td class="num">${r.qty?string("0.##")}</td>
                    <td class="num">${(r.lockedQty)?string("0.##")}</td>
                    <td class="num">${(r.onHandQty)?string("0.##")}</td>
                    <td class="num"><span class="warn">${(r.shortQty)?string("0.##")}</span></td>
                    <td class="num">${(r.suggestQty)?string("0.####")}</td>
                    <td>${r.designators!}</td>
                </tr>
            </#list>
        </table>
    <#elseif bomItems?has_content>
        <p class="hint">这一版的料都锁齐了，没有要买的。</p>
    </#if>

    <#if bomItems?has_content>
        <h4>本版元件（${bomItems?size} 个）</h4>
        <table id="devBomTable">
            <tr>
                <th class="num">#</th><th>料号</th><th>名称</th><th class="num">数量</th>
                <th>位号</th><th>相对上一版</th><th class="num">已锁</th><th class="num">在库</th><th>操作</th>
            </tr>
            <#list bomItems as r>
                <tr data-bom-line="${r.lineSeq}" data-bom-change="${r.changeId}">
                    <td class="num">${r_index + 1}</td>
                    <td><input type="text" name="componentCode" form="bomRow${r.lineSeq}" size="20" value="${r.componentCode}"/></td>
                    <td>${r.componentName!}<#if r.properties??><br/><span class="hint">${r.properties}</span></#if></td>
                    <td class="num"><input type="text" name="qty" form="bomRow${r.lineSeq}" size="5" value="${r.qty?string("0.##")}"/></td>
                    <td>${r.designators!}</td>
                    <td><#if r.changeId == 'MFG_BOM_NEW'><span class="ok">${bomChangeLabel(r.changeId)}</span><#elseif r.changeId == 'MFG_BOM_CHANGED'><span class="warn">${bomChangeLabel(r.changeId)}<#if r.previousQty??>（原 ${(r.previousQty)?string("0.##")}）</#if></span><#else>${bomChangeLabel(r.changeId)}</#if></td>
                    <td class="num">${(r.lockedQty)?string("0.##")}</td>
                    <td class="num">${(r.onHandQty)?string("0.##")}</td>
                    <td>
                        <input type="submit" form="bomRow${r.lineSeq}" value="保存"/>
                        <#if ((r.shortQty)!0) gt 0><br/><span class="warn">缺 ${(r.shortQty)?string("0.##")}</span></#if>
                    </td>
                </tr>
            </#list>
        </table>
        <#list bomItems as r>
            <form id="bomRow${r.lineSeq}" method="post" action="/mfg/control/devBomItemSave">
                <input type="hidden" name="projectCode" value="${project.projectCode}"/>
                <input type="hidden" name="lineSeq" value="${r.lineSeq}"/>
            </form>
        </#list>
        <p class="hint">料号可以和库存对不上：Fritzing 用的是它自己的名字，库存是按买回来的型号记的。
            改这一行的料号（数量也可以改），保存后会按新料号重新锁料，缺多少立刻算出来。</p>
        <form method="post" action="/mfg/control/devBomRelease" class="inline">
            <input type="hidden" name="projectCode" value="${project.projectCode}"/>
            <input type="submit" value="释放本项目的锁料"/>
        </form>
        <span class="hint">板子下单或项目暂停时用：只把锁放掉，库存不动。</span>
    </#if>

    <#if bomDropped?has_content>
        <h4>本版不再使用的元件（${bomDropped?size} 个，按研发消耗处理）</h4>
        <table id="devBomDroppedTable">
            <tr><th>料号</th><th>名称</th><th class="num">上一版数量</th><th class="num">已记研发消耗</th><th>处理</th></tr>
            <#list bomDropped as r>
                <tr data-bom-dropped="${r.componentCode}">
                    <td>${r.componentCode}</td>
                    <td>${r.componentName!}</td>
                    <td class="num"><#if r.previousQty??>${(r.previousQty)?string("0.##")}</#if></td>
                    <td class="num"><#if r.consumedQty??>${(r.consumedQty)?string("0.##")}</#if></td>
                    <td>${r.note!}</td>
                </tr>
            </#list>
        </table>
    </#if>

    <#if bomImports?has_content>
        <h4>导入记录（${bomImports?size} 次）</h4>
        <table id="devBomImportTable">
            <tr>
                <th class="num">#</th><th>版本</th><th>文件</th><th>时间</th><th class="num">元件</th>
                <th class="num">新增</th><th class="num">数量变化</th><th class="num">不再使用</th>
                <th class="num">缺货</th><th class="num">锁料</th>
            </tr>
            <#list bomImports as h>
                <tr data-bom-import="${h.importSeq}">
                    <td class="num">${h.importSeq}</td>
                    <td>${h.bomVersion!}</td>
                    <td>${h.sourceFile!}</td>
                    <td><#if h.importedStamp??>${h.importedStamp?string("MM-dd HH:mm")}</#if></td>
                    <td class="num">${(h.itemCount)!0}</td>
                    <td class="num">${(h.newCount)!0}</td>
                    <td class="num">${(h.changedCount)!0}</td>
                    <td class="num">${(h.droppedCount)!0}</td>
                    <td class="num">${(h.shortCount)!0}</td>
                    <td class="num">${(h.lockedQty)!0}</td>
                </tr>
            </#list>
        </table>
    </#if>

    <form method="post" action="/mfg/control/devBomImport" enctype="multipart/form-data" id="devBomImportForm">
        <input type="hidden" name="projectCode" value="${project.projectCode}"/>
        <fieldset>
            <legend>导入 Fritzing 的 BOM（bom.html 或 CSV）</legend>
            <p>
                文件 <input type="file" name="file" size="46"/>
                <span class="hint">Fritzing 里「导出 → 作为图片 → 物料清单」生成的 &lt;项目&gt;_bom.html</span>
            </p>
            <p>
                版本号 <input type="text" name="bomVersion" size="8" placeholder="v94"/>
                <span class="hint">留空就从文件名里取 vNN</span>
                <label><input type="checkbox" name="lock" value="Y" checked="checked"/> 锁库存</label>
                <label><input type="checkbox" name="consumeDropped" value="Y" checked="checked"/> 不再使用的元件记研发消耗</label>
            </p>
            <p>
                或粘贴内容<br/>
                <textarea name="bomText" rows="6" cols="100" placeholder="也可以把 bom.html 的内容整段粘进来"></textarea>
            </p>
            <p><input type="submit" value="导入这一版 BOM"/></p>
        </fieldset>
    </form>
    <p class="hint">每次导入都会存一份快照并按上一版比对：新出现的元件进缺货清单；数量变大的补锁；
        不再使用的元件<b>不退回库存</b>，按研发消耗（样品）记一笔——面包板和连线阶段的东西已经用掉了。
        锁料是预留，不影响在库数量，但工单就抢不走这几颗料；缺货清单里的「建议采购」= 需求 − 已锁。</p>

    <h3 id="issues">问题清单（未结 ${issueOpen} 个<#if issueHigh gt 0>，其中严重 ${issueHigh} 个</#if> / 共 ${issues?size} 条）</h3>
    <#if issues?has_content>
        <table id="issueTable">
            <tr>
                <th>编号</th><th>步骤</th><th>问题</th><th>严重度</th><th>状态</th>
                <th>负责人</th><th>发现</th><th>截止</th><th>关闭</th><th>说明 / 处理结果</th><th>操作</th>
            </tr>
            <#list issues as issue>
                <tr>
                    <td>${issue.issueId}</td>
                    <td><#if issue.stepId??>${stepLabel(issue.stepId)}<#else><span class="hint">项目级</span></#if></td>
                    <td>${issue.title!}</td>
                    <td><#if (issue.severityId!'') == 'MFG_ISSUE_HIGH'><span class="error">${issueSeverityLabel(issue.severityId)}</span><#else>${issueSeverityLabel(issue.severityId)}</#if></td>
                    <td><#if (issue.statusId!'') == 'MFG_ISSUE_CLOSED'><span class="ok">${issueStatusLabel(issue.statusId)}</span><#else>${issueStatusLabel(issue.statusId)}</#if></td>
                    <td>${issue.ownerName!}</td>
                    <td><#if issue.foundDate??>${issue.foundDate?string("MM-dd")}</#if></td>
                    <td><#if issue.dueDate??><#if (issue.statusId!'') != 'MFG_ISSUE_CLOSED' && issue.dueDate?date?long lt .now?long><span class="error">${issue.dueDate?string("MM-dd")}</span><#else>${issue.dueDate?string("MM-dd")}</#if></#if></td>
                    <td><#if issue.closedDate??>${issue.closedDate?string("MM-dd")}</#if></td>
                    <td>${(issue.detail)!}<#if (issue.resolution!'') != ''><br/><span class="hint">处理：${issue.resolution}</span></#if></td>
                    <td>
                        <#if (issue.statusId!'') != 'MFG_ISSUE_CLOSED'>
                            <form method="post" action="/mfg/control/devIssueStatus" class="inline">
                                <input type="hidden" name="issueId" value="${issue.issueId}"/>
                                <input type="hidden" name="statusId" value="MFG_ISSUE_CLOSED"/>
                                <input type="hidden" name="projectCode" value="${project.projectCode}"/>
                                <input type="submit" value="关闭"/>
                            </form>
                        <#else>
                            <form method="post" action="/mfg/control/devIssueStatus" class="inline">
                                <input type="hidden" name="issueId" value="${issue.issueId}"/>
                                <input type="hidden" name="statusId" value="MFG_ISSUE_OPEN"/>
                                <input type="hidden" name="projectCode" value="${project.projectCode}"/>
                                <input type="submit" value="重开"/>
                            </form>
                        </#if>
                    </td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有问题记录。打样、试产、烧录时发现的坑记在这里：谁改、什么时候必须改完，一眼能看到。</p>
    </#if>

    <fieldset>
        <legend>登记问题（发现即记，别攒到评审）</legend>
        <form method="post" action="/mfg/control/devIssueSave">
            <input type="hidden" name="projectCode" value="${project.projectCode}"/>
            <p>
                步骤
                <select name="stepId">
                    <option value="">项目级（不限步骤）</option>
                    <#list steps as s>
                        <option value="${s.stepId}">${stepLabel(s.stepId)}</option>
                    </#list>
                </select>
                问题 <input type="text" name="title" size="30" placeholder="一句话说清现象"/>
                严重度
                <select name="severityId">
                    <option value="MFG_ISSUE_MEDIUM">一般</option>
                    <option value="MFG_ISSUE_HIGH">严重</option>
                    <option value="MFG_ISSUE_LOW">轻微</option>
                </select>
                负责人 <input type="text" name="ownerName" size="9"/>
                发现日 <input type="text" name="foundDate" size="10" placeholder="yyyy-mm-dd（留空=今天）"/>
                截止 <input type="text" name="dueDate" size="10" placeholder="yyyy-mm-dd"/>
            </p>
            <p>
                说明 <input type="text" name="detail" size="60" placeholder="复现条件、板号、批次、截图文件名"/>
                <input type="submit" value="保存问题"/>
            </p>
        </form>
    </fieldset>

    <h3>阶段关卡</h3>
    <table>
        <tr><th>阶段</th><th>计划评审</th><th>实际评审</th><th>结论</th><th>评审人</th><th>说明</th></tr>
        <#list reviews as r>
            <tr>
                <td><#if (project.stageId!'') == r.stageId><b>${stageLabel(r.stageId)}</b><#else>${stageLabel(r.stageId)}</#if></td>
                <td><#if r.plannedDate??>${r.plannedDate?string("yyyy-MM-dd")}</#if></td>
                <td><#if r.doneDate??>${r.doneDate?string("yyyy-MM-dd")}</#if></td>
                <td><#if r.resultId??>${reviewLabel(r.resultId)}<#else><span class="hint">未评审</span></#if></td>
                <td>${r.reviewer!}</td>
                <td>${r.note!}</td>
            </tr>
        </#list>
    </table>

    <fieldset>
        <legend>登记关卡评审（通过则进入下一阶段）</legend>
        <form method="post" action="/mfg/control/devStageSave">
            <input type="hidden" name="projectCode" value="${project.projectCode}"/>
            <p>
                阶段
                <select name="stageId">
                    <#list reviews as r>
                        <option value="${r.stageId}"<#if (project.stageId!'') == r.stageId> selected="selected"</#if>>${stageLabel(r.stageId)}</option>
                    </#list>
                </select>
                结论
                <select name="resultId">
                    <option value="MFG_REVIEW_PASS">通过</option>
                    <option value="MFG_REVIEW_CONDITIONAL">有条件通过</option>
                    <option value="MFG_REVIEW_FAIL">不通过</option>
                </select>
                评审日 <input type="text" name="doneDate" size="10" placeholder="yyyy-mm-dd"/>
                评审人 <input type="text" name="reviewer" size="10"/>
            </p>
            <p>
                说明 <input type="text" name="note" size="46"/>
                <input type="submit" value="保存评审"/>
            </p>
        </form>
    </fieldset>

    <h3>项目资料</h3>
    <#include "attachmentBlock.ftl">
    <#assign attOwner = project.projectCode>
    <#assign attBack = '/mfg/control/dev?projectCode=' + attOwner>
    <@attachmentBlock 'MFG_ATT_DEV_PROJECT' attOwner attBack '项目资料：3D 打印照片 / 合同扫描件 / Gerber 截图' atts/>

    <h3>相关变更单（${ecos?size} 个）</h3>    <#if ecos?has_content>
        <table>
            <tr><th>变更单</th><th>类型</th><th>状态</th><th>原因</th><th>目标 BOM</th></tr>
            <#list ecos as e>
                <tr>
                    <td><a href="/mfg/control/eco?ecoId=${e.ecoId}">${e.ecoId}</a></td>
                    <td>${e.ecoTypeId!}</td>
                    <td>${e.statusId!}</td>
                    <td>${e.reason!}</td>
                    <td>${e.toBomVersionId!}</td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有变更单，可以到「设计变更」页新建。</p>
    </#if>
</#if>
