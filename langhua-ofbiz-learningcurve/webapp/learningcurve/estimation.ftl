<#ftl encoding="UTF-8">
<#--
  Estimation form.
  Input: productCode, measureType (COST/HOURS), historyData, newUnitNumber, leastSquare, methodType
         mesProduct - optional model code: when given, the batches are taken from the MES work orders
  Submit calls the learningCurveSaveEstimate service and redirects to the stored record.
-->
<#assign p = (parameters)!{}>
<#assign errList = (errorMessageList)![]>
<#assign mes = (mesBatches)!{}>
<#assign mesRows = (mes.batches)![]>
<#assign mesCount = (mes.batchCount)!0>
<#assign mp = (mesPeriods)!{}>
<#assign mpRows = (mp.periods)![]>
<#assign mpCount = (mp.periodCount)!0>
<#assign mesGroupValue = (mp.groupBy)!'WEEK'>
<#-- the period lookup only counts when it was asked for: the plain page keeps the sample data -->
<#assign periodAsked = (p.mesGroupBy)?has_content || (p.mesFrom)?has_content || (p.mesTo)?has_content>
<#if !periodAsked><#assign mpCount = 0></#if>

<#-- the form starts from the MES numbers when they were asked for, otherwise from the sample -->
<#assign historyValue = '2:200;3:285;5:450'>
<#if mesCount gt 0><#assign historyValue = (mes.historyData)!''></#if>
<#if periodAsked && mpCount gt 0><#assign historyValue = (mp.historyData)!''></#if>
<#if (p.historyData)?has_content><#assign historyValue = p.historyData></#if>
<#assign measureValue = 'COST'>
<#if mesCount gt 0 || mpCount gt 0><#assign measureValue = 'HOURS'></#if>
<#if (p.measureType)?has_content><#assign measureValue = p.measureType></#if>
<#assign productValue = (p.productCode)!''>
<#if !(productValue?has_content) && (p.mesProduct)?has_content><#assign productValue = p.mesProduct></#if>

<h1>学习曲线估算</h1>

<fieldset>
    <legend>从 MES 取历史批次（可选）</legend>
    <form method="get" action="/learningcurve/control/main">
        <p>
            <label for="mesProduct">产品型号</label>
            <input type="text" id="mesProduct" name="mesProduct" size="20" value="${(p.mesProduct)!''}"/>
            <input type="submit" class="secondary" value="取数"/>
        </p>
    </form>
    <#if mesRows?has_content>
        <p class="hint">从 MES 取到 ${mesCount} 个已报工的工单：合计 ${((mes.totalQty)!0)?string("0.##")} 台 /
            ${((mes.totalHours)!0)?string("0.##")} 小时，已按工单顺序填进下面的历史批次数据。</p>
        <table>
            <tr><th>工单</th><th>交期</th><th class="num">合格数</th><th class="num">报工工时</th><th class="num">单台工时</th></tr>
            <#list mesRows as row>
                <tr>
                    <td>${row.workOrderId!}</td>
                    <td>${row.date!}</td>
                    <td class="num">${((row.qtyOk)!0)?string("0.##")}</td>
                    <td class="num">${((row.hours)!0)?string("0.##")}</td>
                    <td class="num">${((row.unitHours)!0)?string("0.###")}</td>
                </tr>
            </#list>
        </table>
    <#elseif (mes.message)?has_content>
        <p class="hint">${mes.message}</p>
    <#else>
        <p class="hint">填型号后点「取数」：每个工单算一个批次，数量取报工的合格数，数值取报工工时。</p>
    </#if>
</fieldset>

<fieldset>
    <legend>从 MES 报表取点（按期间，可选）</legend>
    <form method="get" action="/learningcurve/control/main">
        <p>
            <label for="mesProduct2">产品型号</label>
            <input type="text" id="mesProduct2" name="mesProduct" size="18" value="${(p.mesProduct)!''}" placeholder="空 = 全车间"/>
            <label for="mesGroupBy">汇总</label>
            <select id="mesGroupBy" name="mesGroupBy">
                <option value="DAY"<#if mesGroupValue == 'DAY'> selected="selected"</#if>>日</option>
                <option value="WEEK"<#if mesGroupValue == 'WEEK'> selected="selected"</#if>>周</option>
                <option value="MONTH"<#if mesGroupValue == 'MONTH'> selected="selected"</#if>>月</option>
            </select>
            起 <input type="text" name="mesFrom" size="10" value="${(p.mesFrom)!''}" placeholder="yyyy-mm-dd"/>
            止 <input type="text" name="mesTo" size="10" value="${(p.mesTo)!''}" placeholder="yyyy-mm-dd"/>
            <input type="submit" class="secondary" value="取点"/>
        </p>
        <p class="hint">每个期间算一个批次：数量 = 该期间的报工良品数，数值 = 该期间的报工工时；
            这样「报表里的单台工时」直接就喂进曲线，不用手填 <code>数量:工时</code>。</p>
    </form>
    <#if periodAsked && mpRows?has_content>
        <p class="hint">取到 ${mpCount} 个期间：合计 ${((mp.totalQty)!0)?string("0.##")} 台 /
            ${((mp.totalHours)!0)?string("0.##")} 小时，已按时间顺序填进下面的历史批次数据。</p>
        <table id="mesPeriodTable">
            <tr><th>期间</th><th class="num">良品</th><th class="num">工时</th><th class="num">单台工时</th></tr>
            <#list mpRows as row>
                <tr>
                    <td>${row.periodKey!}</td>
                    <td class="num">${((row.qtyOk)!0)?string("0.##")}</td>
                    <td class="num">${((row.hours)!0)?string("0.##")}</td>
                    <td class="num">${((row.unitHours)!0)?string("0.###")}</td>
                </tr>
            </#list>
        </table>
    <#elseif periodAsked && (mp.message)?has_content>
        <p class="hint">${mp.message}</p>
    </#if>
</fieldset>

<form method="post" action="/learningcurve/control/estimate">
    <p>
        <label for="productCode">产品型号 / 项目</label>
        <input type="text" id="productCode" name="productCode" size="30" value="${productValue}"/>
        <span class="hint">required</span>
    </p>
    <p>
        <label for="measureType">口径</label>
        <select id="measureType" name="measureType">
            <option value="COST"<#if measureValue == 'COST'> selected="selected"</#if>>成本（元）</option>
            <option value="HOURS"<#if measureValue == 'HOURS'> selected="selected"</#if>>工时（小时）</option>
        </select>
    </p>
    <p>
        <label for="historyData">历史批次数据</label>
        <input type="text" id="historyData" name="historyData" size="60"
               value="${historyValue}"/>
        <span class="hint">批次数量:该批总额，用 ; 或 , 分隔</span>
    </p>
    <p>
        <label for="newUnitNumber">待估算单位数</label>
        <input type="text" id="newUnitNumber" name="newUnitNumber" size="10"
               value="${p.newUnitNumber!"5"}"/>
    </p>
    <p>
        <label for="leastSquare">期望 R&sup2;</label>
        <input type="text" id="leastSquare" name="leastSquare" size="6"
               value="${p.leastSquare!"0.95"}"/>
        <span class="hint">0 到 1 之间</span>
    </p>
    <p>
        <label for="methodType">方法</label>
        <select id="methodType" name="methodType">
            <option value="1"<#if (p.methodType!"1") == "1"> selected="selected"</#if>>1 - Wright（单位成本随累计产量下降）</option>
            <option value="2"<#if (p.methodType!"") == "2"> selected="selected"</#if>>2 - Crawford（累计平均）</option>
        </select>
    </p>
    <p><input type="submit" value="估算并保存"/></p>
</form>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
