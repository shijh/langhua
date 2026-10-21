<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Product development: project list, the stage ladder and the gate reviews of the selected project.
  Data: listResult.projects / stages / stageId / statusId, productList.products,
        detail.project / detail.reviews / detail.ecos
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

<h1>产品开发</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>立项 / 修改项目</legend>
    <form method="post" action="/mfg/control/devProjectSave">
        <input type="hidden" name="projectCode" value="${project.projectCode!''}"/>
        <p>
            项目名称 <input type="text" name="projectName" size="24" value="${project.projectName!''}"/>
            型号
            <select name="productCode">
                <option value="">（未定）</option>
                <#list products as p>
                    <option value="${p.productCode}"<#if (project.productCode!'') == p.productCode> selected="selected"</#if>>${p.productCode}</option>
                </#list>
            </select>
            责任人 <input type="text" name="ownerName" size="10" value="${project.ownerName!''}"/>
        </p>
        <p>
            阶段
            <select name="stageId">
                <#list stages as s>
                    <option value="${s.stageId}"<#if (project.stageId!'') == s.stageId> selected="selected"</#if>>${stageLabel(s.stageId)}</option>
                </#list>
            </select>
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
            <th>项目号</th><th>名称</th><th>型号</th><th>阶段</th>
            <th class="num">关卡</th><th class="num">未结变更</th>
            <th>责任人</th><th>计划上市</th><th>状态</th>
        </tr>
        <#list projects as p>
            <tr>
                <td><a href="/mfg/control/dev?projectCode=${p.projectCode}">${p.projectCode}</a></td>
                <td>${p.projectName!}</td>
                <td>${p.productCode!}</td>
                <td>${stageLabel(p.stageId!'')}</td>
                <td class="num">${p.passedCount} / ${p.reviewCount}</td>
                <td class="num"><#if (p.openEcoCount!0) gt 0><span class="warn">${p.openEcoCount}</span><#else>0</#if></td>
                <td>${p.ownerName!}</td>
                <td><#if p.plannedLaunchDate??>${p.plannedLaunchDate?string("yyyy-MM-dd")}</#if></td>
                <td>${statusLabel(p.statusId!'')}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有开发项目，先在上面立项。</p>
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
        <li>型号：<#if (project.productCode!'') != ''>${project.productCode}<#else><span class="hint">未关联</span></#if></li>
        <li>当前阶段：<b>${stageLabel(project.stageId!'')}</b><#if project.nextStageId??> → 下一关：${stageLabel(project.nextStageId)}</#if></li>
        <li>目标成本 / 售价：${project.targetCost!0} / ${project.targetPrice!0}
            <#if (project.targetCost!0) gt 0><span class="hint">毛利 ${((project.targetPrice!0) - (project.targetCost!0))?string("0.00")}</span></#if>
        </li>
        <li>计划上市：<#if project.plannedLaunchDate??>${project.plannedLaunchDate?string("yyyy-MM-dd")}<#else>-</#if>
            实际上市：<#if project.actualLaunchDate??>${project.actualLaunchDate?string("yyyy-MM-dd")}<#else>-</#if></li>
    </ul>

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
