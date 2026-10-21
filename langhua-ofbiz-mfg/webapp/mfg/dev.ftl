<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Product development: project list, the track steps, the stage ladder and the gate reviews.
  Data: listResult.projects / stages / stageId / statusId, productList.products,
        detail.project / detail.reviews / detail.ecos / detail.steps / detail.trackId
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
            <th class="num">关卡</th><th class="num">未结变更</th><th>状态</th>
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
                <td>${statusLabel(p.statusId!'')}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有开发项目，先在上面立项。三条线（PCB / 固件 / 外壳）各建一个项目，步骤清单由线路决定。</p>
</#if>
<#if (lr.pageCount?default(1)) gt 1>
    <p class="pager">
        第 ${(lr.page)!1} / ${(lr.pageCount)!1} 页（共 ${(lr.totalCount)!0} 条）
        <#if (lr.page?default(1)) gt 1>
            <a href="/mfg/control/dev?search=${(lr.search)!''?url}&stageId=${(lr.stageId)!''?url}&statusId=${(lr.statusId)!''?url}&page=1">首页</a>
            <a href="/mfg/control/dev?search=${(lr.search)!''?url}&stageId=${(lr.stageId)!''?url}&statusId=${(lr.statusId)!''?url}&page=${(lr.page)-1}">上一页</a>
        </#if>
        <#if (lr.page?default(1)) lt (lr.pageCount?default(1))>
            <a href="/mfg/control/dev?search=${(lr.search)!''?url}&stageId=${(lr.stageId)!''?url}&statusId=${(lr.statusId)!''?url}&page=${(lr.page)+1}">下一页</a>
            <a href="/mfg/control/dev?search=${(lr.search)!''?url}&stageId=${(lr.stageId)!''?url}&statusId=${(lr.statusId)!''?url}&page=${(lr.pageCount)!1}">末页</a>
        </#if>
    </p>
</#if>

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
                <th>单号</th><th>备注</th><th>操作</th>
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

    <h3>相关变更单（${ecos?size} 个）</h3>
    <#if ecos?has_content>
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
