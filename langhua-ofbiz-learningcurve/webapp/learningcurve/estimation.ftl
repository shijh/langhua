<#ftl encoding="UTF-8">
<#--
  Estimation form.
  Input: productCode, measureType (COST/HOURS), historyData, newUnitNumber, leastSquare, methodType
  Submit calls the learningCurveSaveEstimate service and redirects to the stored record.
-->
<#assign p = (parameters)!{}>
<#assign errList = (errorMessageList)![]>

<h1>学习曲线估算</h1>

<form method="post" action="/learningcurve/control/estimate">
    <p>
        <label for="productCode">产品型号 / 项目</label>
        <input type="text" id="productCode" name="productCode" size="30" value="${p.productCode!''}"/>
        <span class="hint">required</span>
    </p>
    <p>
        <label for="measureType">口径</label>
        <select id="measureType" name="measureType">
            <option value="COST"<#if (p.measureType!"COST") == "COST"> selected="selected"</#if>>成本（元）</option>
            <option value="HOURS"<#if (p.measureType!"") == "HOURS"> selected="selected"</#if>>工时（小时）</option>
        </select>
    </p>
    <p>
        <label for="historyData">历史批次数据</label>
        <input type="text" id="historyData" name="historyData" size="45"
               value="${p.historyData!"2:200;3:285;5:450"}"/>
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
