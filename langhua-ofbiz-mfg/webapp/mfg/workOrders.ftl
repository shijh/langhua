<#ftl encoding="UTF-8">
<#import "pager.ftl" as pg>
<#setting url_escaping_charset="UTF-8">
<#--
  Work order list and creation form.
  Data: listResult.workOrders, productList.products, parameters.statusId
-->
<#assign lr = (listResult)!{}>
<#assign orders = (lr.workOrders)![]>
<#assign pl = (productList)!{}>
<#assign products = (pl.products)![]>
<#assign errList = (errorMessageList)![]>

<h1>工单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<fieldset>
    <legend>新建工单</legend>
    <form method="post" action="/mfg/control/workOrderCreate">
        <p>
            产品型号
            <select name="productCode">
                <#list products as p>
                    <option value="${p.productCode}">${p.productCode}<#if p.productName??> ${p.productName}</#if></option>
                </#list>
            </select>
            数量 <input type="text" name="plannedQty" size="6" value="100"/>
            交期 <input type="text" name="dueDate" size="11" placeholder="yyyy-MM-dd"/>
            客户 <input type="text" name="customerName" size="14"/>
            SMT 外协 <input type="text" name="smtVendor" size="12"/>
            <input type="submit" value="创建工单"/>
        </p>
        <#if !products?has_content>
            <p class="hint">还没有产品型号，请先到 <a href="/mfg/control/products">产品与 BOM</a> 创建。</p>
        </#if>
    </form>
</fieldset>

<h2>工单列表</h2>
<form method="get" action="/mfg/control/main">
    搜索 <input type="text" name="search" size="24" value="${(lr.search)!''}" placeholder="工单号 / 型号 / 客户 / 外协"/>
    状态
    <select name="statusId">
        <option value="">（全部）</option>
        <option value="MFG_CREATED"<#if (parameters.statusId!'') == 'MFG_CREATED'> selected="selected"</#if>>已创建</option>
        <option value="MFG_IN_PROGRESS"<#if (parameters.statusId!'') == 'MFG_IN_PROGRESS'> selected="selected"</#if>>生产中</option>
        <option value="MFG_COMPLETED"<#if (parameters.statusId!'') == 'MFG_COMPLETED'> selected="selected"</#if>>已完工</option>
        <option value="MFG_CLOSED"<#if (parameters.statusId!'') == 'MFG_CLOSED'> selected="selected"</#if>>已关闭</option>
    </select>
    <input type="submit" value="查询"/>
    <span class="hint">共 ${(lr.totalCount)!0} 条</span>
</form>
<#if orders?has_content>
    <table id="workOrderTable">
        <tr>
            <th>工单号</th>
            <th>产品型号</th>
            <th>客户</th>
            <th>SMT 外协</th>
            <th class="num">计划数量</th>
            <th class="num">已报工时</th>
            <th>交期</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>工序 / 欠料</th>
        </tr>
        <#list orders as o>
            <#assign woKey = 'wo-' + (o.workOrderId!'')>
            <#assign shortages = (o.shortages)![]>
            <#assign operations = (o.operations)![]>
            <tr>
                <td><a href="/mfg/control/workOrder?workOrderId=${o.workOrderId}">${o.workOrderId}</a></td>
                <td>${o.productCode!}</td>
                <td>${o.customerName!}</td>
                <td>${o.smtVendor!}</td>
                <td class="num">${(o.plannedQty)?string("0")}</td>
                <td class="num">${(o.reportedHours)?string("0.##")}</td>
                <td><#if o.dueDate??>${o.dueDate?string("yyyy-MM-dd")}</#if></td>
                <td>${o.statusId!}</td>
                <td><#if o.createdStamp??>${o.createdStamp?string("MM-dd HH:mm")}</#if></td>
                <td><#if shortages?has_content><span class="error">欠 ${shortages?size}</span> </#if><a href="#" data-toggle-lines="${woKey}" data-open="N">${operations?size} 道工序 <span>▸</span></a></td>
            </tr>
            <#if operations?has_content || shortages?has_content>
                <tr data-lines="${woKey}"><td colspan="10" class="lines">
                    <#if shortages?has_content>
                        <table class="inner" data-shortages="${o.workOrderId}">
                            <tr><th>缺料</th><th>名称</th><th class="num">需求</th><th class="num">已发</th><th class="num">已预留</th><th class="num">还缺</th><th>关键件</th></tr>
                            <#list shortages as line>
                                <tr data-shortage="${line.componentCode!}">
                                    <td>${line.componentCode!}</td>
                                    <td>${line.componentName!}</td>
                                    <td class="num">${(line.requiredQty!0)?string("0.##")}</td>
                                    <td class="num">${(line.issuedQty!0)?string("0.##")}</td>
                                    <td class="num">${(line.reservedQty!0)?string("0.##")}</td>
                                    <td class="num"><span class="error">${(line.shortage!0)?string("0.##")}</span></td>
                                    <td><#if (line.isKeyPart!'') == 'Y'><span class="warn">关键</span></#if></td>
                                </tr>
                            </#list>
                        </table>
                    <#else>
                        <p class="hint">齐套：BOM 上的料都发够或已预留。</p>
                    </#if>
                    <#if operations?has_content>
                        <table class="inner" data-operations="${o.workOrderId}">
                            <tr><th>工序</th><th>名称</th><th>工作中心</th><th class="num">标准工时</th><th class="num">已报工时</th></tr>
                            <#list operations as op>
                                <tr data-op-seq="${op.opSeq!}">
                                    <td>${op.opSeq!}</td>
                                    <td>${op.opName!}</td>
                                    <td>${op.workCenter!}</td>
                                    <td class="num">${(op.stdHours!0)?string("0.##")}</td>
                                    <td class="num">${(op.reportedHours!0)?string("0.##")}</td>
                                </tr>
                            </#list>
                        </table>
                    <#else>
                        <p class="hint">这个型号还没有工序模板，工时报在工单上。</p>
                    </#if>
                </td></tr>
            </#if>
        </#list>
    </table>
<#else>
    <p class="hint">还没有工单。</p>
</#if>
<@pg.pager action="/mfg/control/main" query=('search=' + ((lr.search)!'')?url + '&' + 'statusId=' + ((parameters.statusId)!'')?url)
    page=(lr.page)!1 pageCount=(lr.pageCount)!1 total=(lr.totalCount)!0 unit="条" pageSize=(lr.pageSize)!20/>

