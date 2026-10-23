<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  Engineering change orders: list, raise a change, approve it, apply it (switches the BOM version).
  Data: listResult.ecos / statusId / openCount, productList.products, projectList.projects,
        detail.eco / detail.fromVersion / detail.toVersion / detail.versions
-->
<#assign lr = (listResult)!{}>
<#assign ecos = (lr.ecos)![]>
<#assign pl = (productList)!{}>
<#assign products = (pl.products)![]>
<#assign pjl = (projectList)!{}>
<#assign projects = (pjl.projects)![]>
<#assign d = (detail)!{}>
<#assign eco = (d.eco)!{}>
<#assign fromVersion = (d.fromVersion)!{}>
<#assign toVersion = (d.toVersion)!{}>
<#assign versions = (d.versions)![]>
<#assign errList = (errorMessageList)![]>

<#function typeLabel ecoTypeId>
    <#if (ecoTypeId!'') == 'MFG_ECO_BOM'><#return 'BOM 变更'>
    <#elseif (ecoTypeId!'') == 'MFG_ECO_PROCESS'><#return '工艺变更'>
    <#elseif (ecoTypeId!'') == 'MFG_ECO_DESIGN'><#return '设计变更'>
    <#elseif (ecoTypeId!'') == 'MFG_ECO_MATERIAL'><#return '物料替代'>
    <#else><#return '${ecoTypeId!}'>
    </#if>
</#function>
<#function statusLabel statusId>
    <#if (statusId!'') == 'MFG_ECO_DRAFT'><#return '草稿'>
    <#elseif (statusId!'') == 'MFG_ECO_APPROVED'><#return '已批准'>
    <#elseif (statusId!'') == 'MFG_ECO_APPLIED'><#return '已生效'>
    <#elseif (statusId!'') == 'MFG_ECO_CANCELLED'><#return '已取消'>
    <#else><#return '${statusId!}'>
    </#if>
</#function>

<h1>设计变更</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>新建变更单</legend>
    <form method="post" action="/mfg/control/ecoSave">
        <p>
            型号
            <select name="productCode">
                <#list products as p>
                    <option value="${p.productCode}">${p.productCode}<#if p.productName??> - ${p.productName}</#if></option>
                </#list>
            </select>
            类型
            <select name="ecoTypeId">
                <option value="MFG_ECO_BOM">BOM 变更</option>
                <option value="MFG_ECO_PROCESS">工艺变更</option>
                <option value="MFG_ECO_DESIGN">设计变更</option>
                <option value="MFG_ECO_MATERIAL">物料替代</option>
            </select>
            关联项目
            <select name="projectCode">
                <option value="">（无）</option>
                <#list projects as p>
                    <option value="${p.projectCode}">${p.projectCode}</option>
                </#list>
            </select>
            发起人 <input type="text" name="requestedBy" size="10"/>
        </p>
        <p>
            原因 <input type="text" name="reason" size="46"/>
        </p>
        <p>
            变更内容 <input type="text" name="changeSummary" size="70" placeholder="例如：U1 换成 U1-BT5-SOC2，C4 由 100nF 改为 1uF"/>
            <input type="submit" value="建立变更单"/>
        </p>
    </form>
</fieldset>

<h2>变更单列表</h2>
<form method="get" action="/mfg/control/eco">
    搜索 <input type="text" name="search" size="22" value="${(lr.search)!''}" placeholder="变更单号 / 型号 / 原因"/>
    状态
    <select name="statusId">
        <option value="">全部</option>
        <option value="MFG_ECO_DRAFT"<#if (lr.statusId!'') == 'MFG_ECO_DRAFT'> selected="selected"</#if>>草稿</option>
        <option value="MFG_ECO_APPROVED"<#if (lr.statusId!'') == 'MFG_ECO_APPROVED'> selected="selected"</#if>>已批准</option>
        <option value="MFG_ECO_APPLIED"<#if (lr.statusId!'') == 'MFG_ECO_APPLIED'> selected="selected"</#if>>已生效</option>
        <option value="MFG_ECO_CANCELLED"<#if (lr.statusId!'') == 'MFG_ECO_CANCELLED'> selected="selected"</#if>>已取消</option>
    </select>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(lr.totalCount)!0} 条，未结 ${(lr.openCount)!0} 条</span>
</form>

<#if ecos?has_content>
    <table>
        <tr>
            <th>变更单</th><th>型号</th><th>类型</th><th>状态</th><th>原因</th>
            <th>BOM 版本</th><th>发起人</th><th>生效日</th>
        </tr>
        <#list ecos as e>
            <tr>
                <td><a href="/mfg/control/eco?ecoId=${e.ecoId}">${e.ecoId}</a></td>
                <td>${e.productCode!}</td>
                <td>${typeLabel(e.ecoTypeId!'')}</td>
                <td><#if e.statusId == 'MFG_ECO_APPLIED'><span class="ok">${statusLabel(e.statusId)}</span>
                    <#elseif e.statusId == 'MFG_ECO_CANCELLED'><span class="hint">${statusLabel(e.statusId)}</span>
                    <#elseif e.statusId == 'MFG_ECO_APPROVED'><span class="warn">${statusLabel(e.statusId)}</span>
                    <#else>${statusLabel(e.statusId)}</#if></td>
                <td>${e.reason!}</td>
                <td><#if (e.fromBomVersionId!'') != ''>${e.fromBomVersionId}<#if (e.toBomVersionId!'') != ''> → ${e.toBomVersionId}</#if></#if></td>
                <td>${e.requestedBy!}</td>
                <td><#if e.effectiveDate??>${e.effectiveDate?string("yyyy-MM-dd")}</#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有变更单。</p>
</#if>
<@pg.pager action="/mfg/control/eco" query=('search=' + ((lr.search)!'')?url + '&' + 'statusId=' + ((lr.statusId)!'')?url)
    page=(lr.page)!1 pageCount=(lr.pageCount)!1 total=(lr.totalCount)!0 unit="条" pageSize=(lr.pageSize)!20/>

<#if eco.ecoId??>
    <h2>${eco.ecoId}（${statusLabel(eco.statusId!'')}）</h2>
    <table>
        <tr><th>型号</th><td>${eco.productCode!}</td></tr>
        <tr><th>类型</th><td>${typeLabel(eco.ecoTypeId!'')}</td></tr>
        <tr><th>关联项目</th><td>${eco.projectCode!}</td></tr>
        <tr><th>原因</th><td>${eco.reason!}</td></tr>
        <tr><th>变更内容</th><td>${eco.changeSummary!}</td></tr>
        <tr><th>BOM 版本</th><td>
            ${eco.fromBomVersionId!'-'}
            <#if (eco.toBomVersionId!'') != ''> → <b>${eco.toBomVersionId}</b></#if>
        </td></tr>
        <tr><th>发起 / 批准</th><td>${eco.requestedBy!'-'} / ${eco.approvedBy!'-'}</td></tr>
        <tr><th>发起日 / 生效日</th><td>
            <#if eco.requestedDate??>${eco.requestedDate?string("yyyy-MM-dd")}<#else>-</#if> /
            <#if eco.effectiveDate??>${eco.effectiveDate?string("yyyy-MM-dd")}<#else>-</#if>
        </td></tr>
    </table>

    <#if eco.statusId != 'MFG_ECO_APPLIED'>
        <fieldset>
            <legend>指定目标 BOM 版本（生效时切换）</legend>
            <form method="post" action="/mfg/control/ecoSave">
                <input type="hidden" name="ecoId" value="${eco.ecoId}"/>
                <input type="hidden" name="productCode" value="${eco.productCode}"/>
                <p>
                    目标版本
                    <select name="toBomVersionId">
                        <option value="">（不切换 BOM）</option>
                        <#list versions as v>
                            <option value="${v.bomVersionId}"<#if (eco.toBomVersionId!'') == v.bomVersionId> selected="selected"</#if>>${v.bomVersionId}（${v.itemCount} 行）</option>
                        </#list>
                    </select>
                    批准人 <input type="text" name="approvedBy" size="10" value="${eco.approvedBy!''}"/>
                    <input type="submit" value="保存"/>
                </p>
            </form>
        </fieldset>
    </#if>

    <p>
        <#if eco.statusId == 'MFG_ECO_DRAFT'>
            <form method="post" action="/mfg/control/ecoApprove" class="inline">
                <input type="hidden" name="ecoId" value="${eco.ecoId}"/>
                <input type="hidden" name="approvedBy" value="${eco.approvedBy!''}"/>
                <input type="submit" value="批准"/>
            </form>
        </#if>
        <#if eco.statusId == 'MFG_ECO_APPROVED'>
            <form method="post" action="/mfg/control/ecoApply" class="inline">
                <input type="hidden" name="ecoId" value="${eco.ecoId}"/>
                <input type="submit" value="生效（切换到目标 BOM）"/>
            </form>
        </#if>
        <#if eco.statusId == 'MFG_ECO_DRAFT' || eco.statusId == 'MFG_ECO_APPROVED'>
            <form method="post" action="/mfg/control/ecoCancel" class="inline">
                <input type="hidden" name="ecoId" value="${eco.ecoId}"/>
                <input type="submit" value="取消变更"/>
            </form>
        </#if>
        <span class="hint">草稿 → 批准 → 生效；生效时若指定了目标版本，型号的当前 BOM 会切过去。</span>
    </p>
</#if>
