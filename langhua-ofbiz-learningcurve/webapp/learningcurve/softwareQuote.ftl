<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Software quote: the four steps of the assessment standard on one page. The arithmetic lives in the
  MES component (mfgSoftwareEstimate), this page only shows the result and keeps the quotation.
  Data: quoteResult.* (learningCurveSoftwareQuote), quoteList.* (learningCurveListSoftwareQuotes),
        pdr.* (learningCurvePdrForecast)
-->
<#assign q = (quoteResult)!{}>
<#assign saved = (quoteList)!{}>
<#assign quotes = (saved.quotes)![]>
<#assign last = (saved.lastQuote)!{}>
<#assign forecast = (pdr)!{}>
<#assign samples = (forecast.samples)![]>
<#assign rateDefault = ((last.dayRate)!1760)?string("0.##")>
<#assign pdrDefault = ((last.pdrHoursPerFp)!40)?string("0.##")>
<#assign swfBaseDefault = ((last.swfBase)!1.5)?string("0.###")>
<#assign taxDefault = ((last.taxRate)!0.06)?string("0.###")>
<#assign dncDefault = ((last.dncRate)!0.05)?string("0.###")>
<#assign sizeInput = ((parameters.ilfCount)!'')?has_content
        || ((parameters.eifCount)!'')?has_content || ((parameters.ufp)!'')?has_content>

<h1>软件报价</h1>
<p class="hint">规模口径：UFP = 35 × ILF + 15 × EIF（预估功能点法）；工作量 AE = PDR × UFP × SWF × RDF；
    工期 D = 1.277 × (AE/176)<sup>0.404</sup>；金额 = 人天 × 日费率 + 增值税 + 直接非人力成本。
    公式与默认值在 MES 的软件项目里，这里只调参数、出报价、存记录。</p>

<#if forecast.predictedPdr??>
    <div class="cards">
        <div class="card">
            <div class="label">下一个项目预测 PDR</div>
            <div class="value">${forecast.predictedPdr?string("0.##")}<span class="hint"> 人时/FP</span></div>
        </div>
        <div class="card">
            <div class="label">样本（已完成项目）</div>
            <div class="value">${(forecast.sampleCount)!0}</div>
        </div>
        <div class="card">
            <div class="label">斜率</div>
            <div class="value">${forecast.slope?string("0.###")}</div>
        </div>
    </div>
    <p class="hint">按 Wright 曲线拟合 MES 台账里已完成项目的实测 PDR（实际工时 ÷ UFP）；
        斜率小于 1 表示越做越省。<a href="/learningcurve/control/quote?pdrHoursPerFp=${forecast.predictedPdr?string("0.##")}">用这个 PDR 试算</a></p>
<#elseif forecast.message??>
    <p class="hint">PDR 预测：${forecast.message}</p>
</#if>

<h2>参数</h2>
<form method="get" action="/learningcurve/control/quote">
    <p>
        项目名称 <input type="text" name="projectName" size="20" value="${(parameters.projectName)!''}"/>
        客户 <input type="text" name="customerName" size="14" value="${(parameters.customerName)!''}"/>
    </p>
    <p>
        ILF <input type="text" name="ilfCount" size="5" value="${(parameters.ilfCount)!''}"/>
        EIF <input type="text" name="eifCount" size="5" value="${(parameters.eifCount)!''}"/>
        或直接填 UFP <input type="text" name="ufp" size="5" value="${(parameters.ufp)!''}"/>
    </p>
    <p>
        SWF 基础 <input type="text" name="swfBase" size="4" value="${(parameters.swfBase)!swfBaseDefault}"/>
        复用 <input type="text" name="swfReuse" size="4" value="${(parameters.swfReuse)!'0'}"/>
        前期投入 <input type="text" name="swfPreInvest" size="4" value="${(parameters.swfPreInvest)!'0'}"/>
        质量 <input type="text" name="swfQuality" size="4" value="${(parameters.swfQuality)!'0'}"/>
        RDF <input type="text" name="rdf" size="4" value="${(parameters.rdf)!'1'}"/>
    </p>
    <p>
        PDR（人时/FP）<input type="text" name="pdrHoursPerFp" size="5" value="${(parameters.pdrHoursPerFp)!pdrDefault}"/>
        日费率（元/人天）<input type="text" name="dayRate" size="6" value="${(parameters.dayRate)!rateDefault}"/>
        税率 <input type="text" name="taxRate" size="5" value="${(parameters.taxRate)!taxDefault}"/>
        非人力成本率 <input type="text" name="dncRate" size="5" value="${(parameters.dncRate)!dncDefault}"/>
        <input type="submit" value="试算"/>
    </p>
</form>

<#if sizeInput>
    <h2>结果</h2>
    <table id="quoteResult">
        <tr><th>规模 UFP</th><th>SWF</th><th>工作量（人时）</th><th>人天</th><th>工期（人月）</th>
            <th class="num">税前</th><th class="num">增值税</th><th class="num">含税</th>
            <th class="num">非人力成本</th><th class="num">合计</th><th class="num">元/功能点</th></tr>
        <tr>
            <td>${q.ufp?string("0.##")}</td>
            <td>${(q.swf)?string("0.###")}</td>
            <td>${(q.effortHours)?string("0.#")}</td>
            <td>${(q.effortDays)?string("0.##")}</td>
            <td>${(q.scheduleMonths)?string("0.##")}</td>
            <td class="num">${(q.amountBeforeTax)?string("#,##0.00")}</td>
            <td class="num">${(q.taxAmount)?string("#,##0.00")}</td>
            <td class="num">${(q.amountWithTax)?string("#,##0.00")}</td>
            <td class="num">${(q.dncAmount)?string("#,##0.00")}</td>
            <td class="num"><b>${(q.totalAmount)?string("#,##0.00")}</b></td>
            <td class="num">${(q.costPerFp)?string("#,##0.00")}</td>
        </tr>
    </table>

    <h2>保存报价</h2>
    <fieldset>
        <legend>把这次的参数与结果一起存下来（以后改费率不影响历史报价）</legend>
        <form method="post" action="/learningcurve/control/quoteSave">
            <p>
                项目名称 <input type="text" name="projectName" size="20" value="${(parameters.projectName)!''}"/>
                客户 <input type="text" name="customerName" size="14" value="${(parameters.customerName)!''}"/>
                备注 <input type="text" name="note" size="24"/>
            </p>
            <input type="hidden" name="ilfCount" value="${(parameters.ilfCount)!''}"/>
            <input type="hidden" name="eifCount" value="${(parameters.eifCount)!''}"/>
            <input type="hidden" name="ufp" value="${(parameters.ufp)!''}"/>
            <input type="hidden" name="swfBase" value="${(parameters.swfBase)!swfBaseDefault}"/>
            <input type="hidden" name="swfReuse" value="${(parameters.swfReuse)!'0'}"/>
            <input type="hidden" name="swfPreInvest" value="${(parameters.swfPreInvest)!'0'}"/>
            <input type="hidden" name="swfQuality" value="${(parameters.swfQuality)!'0'}"/>
            <input type="hidden" name="rdf" value="${(parameters.rdf)!'1'}"/>
            <input type="hidden" name="pdrHoursPerFp" value="${(parameters.pdrHoursPerFp)!pdrDefault}"/>
            <input type="hidden" name="dayRate" value="${(parameters.dayRate)!rateDefault}"/>
            <input type="hidden" name="taxRate" value="${(parameters.taxRate)!taxDefault}"/>
            <input type="hidden" name="dncRate" value="${(parameters.dncRate)!dncDefault}"/>
            <p><input type="submit" value="保存报价"/></p>
        </form>
    </fieldset>
<#else>
    <p class="hint">填 ILF / EIF（或直接填 UFP）后点「试算」。</p>
</#if>

<h2>已保存的报价（${(saved.quoteCount)!0} 条，合计 ${((saved.totalAmount)!0)?string("#,##0.00")}）</h2>
<#if quotes?has_content>
    <table id="quoteList">
        <tr><th>编号</th><th>项目</th><th>客户</th><th class="num">UFP</th><th class="num">人天</th>
            <th class="num">工期（人月）</th><th class="num">含税</th><th class="num">合计</th>
            <th class="num">日费率</th><th>时间</th><th>备注</th></tr>
        <#list quotes as x>
            <tr>
                <td>${(x.quoteId)!''}</td>
                <td>${(x.projectName)!''}</td>
                <td>${(x.customerName)!''}</td>
                <td class="num">${((x.ufp)!0)?string("0.##")}</td>
                <td class="num">${((x.effortDays)!0)?string("0.##")}</td>
                <td class="num">${((x.scheduleMonths)!0)?string("0.##")}</td>
                <td class="num">${((x.amountWithTax)!0)?string("#,##0.00")}</td>
                <td class="num"><b>${((x.totalAmount)!0)?string("#,##0.00")}</b></td>
                <td class="num"><#if x.dayRate??>${x.dayRate?string("0.##")}</#if></td>
                <td><#if x.createdStamp??>${x.createdStamp?string("MM-dd HH:mm")}</#if></td>
                <td>${(x.note)!''}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有保存过报价。</p>
</#if>
