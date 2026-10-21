<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Software projects: the size of the work, the estimate the standard gives (function points, effort,
  schedule, price) and what the project really took, so the next estimate rests on real numbers.
  Data: swResult.* (mfgSoftwareProjectList), swPreview.* (mfgSoftwareEstimate), productList.products
-->
<#assign r = (swResult)!{}>
<#assign rows = (r.projects)![]>
<#assign pv = (swPreview)!{}>
<#assign products = (productList.products)![]>
<#assign errList = (errorMessageList)![]>
<#macro swCat code>
    <#if (code!'') == 'MFG_SW_FIRMWARE'>固件
    <#elseif (code!'') == 'MFG_SW_ENCLOSURE'>外壳设计
    <#elseif (code!'') == 'MFG_SW_HOST'>上位机 / 工具
    <#elseif (code!'') == 'MFG_SW_PLATFORM'>平台 / 系统
    <#elseif (code!'') == 'MFG_SW_OTHER'>其它
    <#else>-</#if>
</#macro>
<#macro swStatus code>
    <#if (code!'') == 'MFG_SW_PLANNED'>计划
    <#elseif (code!'') == 'MFG_SW_DOING'>进行中
    <#elseif (code!'') == 'MFG_SW_DONE'>已完成
    <#elseif (code!'') == 'MFG_SW_CANCELLED'>已取消
    <#else>${(code!'')}</#if>
</#macro>

<h1>软件项目</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<div class="cards">
    <div class="card">
        <div class="label">项目数</div>
        <div class="value">${(r.projectCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">进行中</div>
        <div class="value">${(r.openCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">超人天预估</div>
        <div class="value">${(r.overCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">已完成</div>
        <div class="value">${(r.finishedCount)?string("0")}</div>
    </div>
    <div class="card">
        <div class="label">计划 / 实际人天（已完成）</div>
        <div class="value">${((r.plannedDaysTotal)!0)?string("0.#")} / ${((r.actualDaysTotal)!0)?string("0.#")}</div>
    </div>
    <div class="card">
        <div class="label">实测功能点耗时率（人时/功能点）</div>
        <div class="value"><#if ((r.pdrSampleCount)!0) gt 0>${((r.pdrMedian)!0)?string("0.##")}<span class="hint">（${(r.pdrSampleCount)!0} 个样本）</span><#else><span class="hint">样本不足</span></#if></div>
    </div>
</div>

<h2>估算（功能点口径）</h2>
<p class="hint">未调整功能点 = 35 × 内部逻辑文件 + 15 × 外部接口文件；
    工作量 = 功能点耗时率 × 功能点 × 软件因素调整因子 × 开发因素调整因子；工期 = 1.277 × (工作量/176)<sup>0.404</sup>；
    金额 = 人天 × 日费率，再加增值税与直接非人力成本。默认值取自 2019 年评估案例，可逐项改。
    实测耗时率中位数 ${((r.pdrMedian)!0)?string("0.##")} 人时/功能点（样本 ${(r.pdrSampleCount)!0}），行业基准 ${((r.defaultPdr)!0)?string("0")}。</p>
<form method="get" action="/mfg/control/software">
    <p>
        内部逻辑文件 <input type="text" name="ilfCount" size="5" value="${(parameters.ilfCount)!''}"/>
        外部接口文件 <input type="text" name="eifCount" size="5" value="${(parameters.eifCount)!''}"/>
        或直接填未调整功能点 <input type="text" name="ufp" size="5" value="${(parameters.ufp)!''}"/>
    </p>
    <p>
        软件因素：基础 <input type="text" name="swfBase" size="4" value="${(parameters.swfBase)!'1.5'}"/>
        复用 <input type="text" name="swfReuse" size="4" value="${(parameters.swfReuse)!'0'}"/>
        前期投入 <input type="text" name="swfPreInvest" size="4" value="${(parameters.swfPreInvest)!'0'}"/>
        质量 <input type="text" name="swfQuality" size="4" value="${(parameters.swfQuality)!'0'}"/>
        开发因素调整因子 <input type="text" name="rdf" size="4" value="${(parameters.rdf)!'1'}"/>
    </p>
    <p>
        功能点耗时率（人时/功能点）<input type="text" name="pdrHoursPerFp" size="5" value="${(parameters.pdrHoursPerFp)!'40'}"/>
        日费率 <input type="text" name="dayRate" size="6" value="${(parameters.dayRate)!'1760'}"/>
        税率 <input type="text" name="taxRate" size="5" value="${(parameters.taxRate)!'0.06'}"/>
        非人力成本率 <input type="text" name="dncRate" size="5" value="${(parameters.dncRate)!'0.05'}"/>
        <input type="submit" value="试算"/>
    </p>
</form>

<#if pv?has_content && pv.ufp??>
    <table id="swPreview">
        <tr><th>功能点</th><th>软件因素调整因子</th><th>工作量（人时）</th><th>人天</th><th>工期（人月）</th>
            <th class="num">税前</th><th class="num">增值税</th><th class="num">含税</th>
            <th class="num">直接非人力成本</th><th class="num">合计</th></tr>
        <tr>
            <td>${pv.ufp?string("0.##")}</td>
            <td>${pv.swf?string("0.###")}</td>
            <td>${(pv.effortHours)?string("0.#")}</td>
            <td>${(pv.effortDays)?string("0.##")}</td>
            <td>${(pv.scheduleMonths)?string("0.##")}</td>
            <td class="num">${(pv.amountBeforeTax)?string("0.00")}</td>
            <td class="num">${(pv.taxAmount)?string("0.00")}</td>
            <td class="num">${(pv.amountWithTax)?string("0.00")}</td>
            <td class="num">${(pv.dncAmount)?string("0.00")}</td>
            <td class="num"><b>${(pv.totalAmount)?string("0.00")}</b></td>
        </tr>
    </table>
</#if>

<h2>新建 / 更新项目</h2>
<fieldset>
    <legend>软件项目（编号留空自动生成 SW-yyMMdd-NN）</legend>
    <form method="post" action="/mfg/control/softwareProjectSave">
        <input type="hidden" name="projectCode" value="${(parameters.projectCode)!''}"/>
        <p>
            名称 <input type="text" name="projectName" size="24" value="${(parameters.projectName)!''}"/>
            类别
            <select name="categoryId">
                <option value="MFG_SW_FIRMWARE">固件</option>
                <option value="MFG_SW_ENCLOSURE">外壳设计</option>
                <option value="MFG_SW_HOST">上位机 / 工具</option>
                <option value="MFG_SW_PLATFORM">平台 / 系统</option>
                <option value="MFG_SW_OTHER">其它</option>
            </select>
            状态
            <select name="statusId">
                <option value="MFG_SW_PLANNED">计划</option>
                <option value="MFG_SW_DOING">进行中</option>
                <option value="MFG_SW_DONE">已完成</option>
                <option value="MFG_SW_CANCELLED">已取消</option>
            </select>
            负责人 <input type="text" name="ownerName" size="10"/>
            同类第几次交付 <input type="text" name="deliverySeq" size="4"/>
        </p>
        <p>
            型号
            <select name="productCode">
                <option value="">（不关联）</option>
                <#list products as p>
                    <option value="${p.productCode}">${p.productCode}　${(p.productName)!''}</option>
                </#list>
            </select>
            开发项目编号 <input type="text" name="devProjectCode" size="16"/>
        </p>
        <p>
            内部逻辑文件 <input type="text" name="ilfCount" size="5"/>
            外部接口文件 <input type="text" name="eifCount" size="5"/>
            未调整功能点（可空，空则按两个文件数算）<input type="text" name="ufp" size="5"/>
            功能点耗时率 <input type="text" name="pdrHoursPerFp" size="5"/>
            日费率 <input type="text" name="dayRate" size="6"/>
            税率 <input type="text" name="taxRate" size="5"/>
        </p>
        <p>
            软件因素：基础 <input type="text" name="swfBase" size="4"/>
            复用 <input type="text" name="swfReuse" size="4"/>
            前期投入 <input type="text" name="swfPreInvest" size="4"/>
            质量 <input type="text" name="swfQuality" size="4"/>
            开发因素 <input type="text" name="rdf" size="4"/>
            非人力成本率 <input type="text" name="dncRate" size="5"/>
        </p>
        <p>
            计划人天（空则按估算冻结）<input type="text" name="plannedDays" size="6"/>
            计划开始 <input type="text" name="plannedStartDate" size="10" placeholder="2026-11-01"/>
            计划结束 <input type="text" name="plannedEndDate" size="10" placeholder="2026-12-15"/>
        </p>
        <p>
            实际工时(h) <input type="text" name="actualHours" size="7"/>
            实际成本 <input type="text" name="actualCost" size="8"/>
            对外报价 <input type="text" name="quotedAmount" size="10"/>
            备注 <input type="text" name="note" size="28"/>
        </p>
        <p><input type="submit" value="保存项目"/></p>
    </form>
</fieldset>

<h2>项目台账（${(r.totalCount)!0} 个）</h2>
<form method="get" action="/mfg/control/software">
    搜索 <input type="text" name="search" size="20" value="${(r.search)!''}" placeholder="编号 / 名称 / 类别 / 负责人"/>
    <input type="submit" value="查询"/>
</form>

<#if rows?has_content>
    <table id="swLedger">
        <tr><th>编号</th><th>名称</th><th>类别</th><th>状态</th><th class="num">功能点</th>
            <th class="num">计划人天</th><th class="num">实际人天</th><th class="num">偏差</th>
            <th class="num">实测耗时率</th><th>计划结束</th><th class="num">含税报价</th><th>备注</th></tr>
        <#list rows as x>
            <tr>
                <td><a href="/mfg/control/software?projectCode=${x.projectCode}">${x.projectCode}</a></td>
                <td>${(x.projectName)!''}</td>
                <td><@swCat (x.categoryId)!''/></td>
                <td><@swStatus (x.statusId)!''/></td>
                <td class="num">${((x.ufp)!0)?string("0.##")}</td>
                <td class="num">${((x.plannedDays)!0)?string("0.##")}</td>
                <td class="num">${((x.actualDays)!0)?string("0.##")}</td>
                <td class="num"><#if x.deviationRate??><#if (x.deviationRate) gt 0><span class="error">+${x.deviationRate?string("0.#")}%</span><#else><span class="ok">${x.deviationRate?string("0.#")}%</span></#if><#else><span class="hint">—</span></#if></td>
                <td class="num"><#if x.pdrActual??>${x.pdrActual?string("0.##")}<#else><span class="hint">—</span></#if></td>
                <td><#if x.plannedEndDate??>${x.plannedEndDate?string("yyyy-MM-dd")}</#if>
                    <#if x.daysToEnd?? && (x.statusId!'') != 'MFG_SW_DONE' && (x.statusId!'') != 'MFG_SW_CANCELLED'>
                        <#if (x.daysToEnd) lt 0><span class="error">超期 ${((x.daysToEnd)?abs)?string("0")} 天</span>
                        <#elseif (x.daysToEnd) lte 3><span class="warn">剩 ${x.daysToEnd} 天</span></#if>
                    </#if></td>
                <td class="num"><#if x.amountWithTax??>${x.amountWithTax?string("#,##0.00")}</#if></td>
                <td>${(x.note)!''}</td>
            </tr>
        </#list>
    </table>
    <p class="hint">共 ${(r.totalCount)!0} 个，第 ${(r.page)!1} / ${(r.pageCount)!1} 页。
        “偏差”= 实际 − 计划，正数表示超人天；“实测耗时率”= 实际工时 ÷ 功能点数（人时/功能点），是计划下一个项目的依据。</p>
<#else>
    <p class="hint">还没有软件项目。上面填一个型号的一次开发工作（例如某个固件的版本），以后估算就有依据了。</p>
</#if>
