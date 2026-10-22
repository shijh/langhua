<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  库位与移库：库位主档（仓 / 区 / 架）+ 每个库位现在的在库量 + 「批次 × 库位」的结存 +
  移库表单（同一批次在两个库位之间搬，写一对流水，批次总量不变）。
  数据：locationResult.locations（locationId / locationName / locationTypeId / parentLocationId /
        onHandQty / lotCount / note）、locationResult.unassignedQty / onHandTotal、
        locationStock.rows（lotId / locationId / lotKindId / componentCode / componentName / onHandQty）
-->
<#assign lr = (locationResult)!{}>
<#assign locations = (lr.locations)![]>
<#assign ls = (locationStock)!{}>
<#assign rows = (ls.rows)![]>
<#assign tr = (transferResult)!{}>
<#assign transfers = (tr.rows)![]>
<#assign errList = (errorMessageList)![]>

<#function locLabel id>
    <#if (id!'') == 'MFG_LOC_WAREHOUSE'><#return '仓'>
    <#elseif (id!'') == 'MFG_LOC_ZONE'><#return '区'>
    <#elseif (id!'') == 'MFG_LOC_BIN'><#return '架'>
    <#else><#return '${id!}'>
    </#if>
</#function>

<h1>库位与移库</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p class="cards">
    <span class="card"><span class="label">库位</span><span class="value">${locations?size}</span></span>
    <span class="card"><span class="label">在库合计</span><span class="value">${((lr.onHandTotal)!0)?string("0.##")}</span></span>
    <span class="card"><span class="label">未分配库位</span><span class="value">${((lr.unassignedQty)!0)?string("0.##")}</span></span>
</p>

<fieldset>
    <legend>新建 / 修改库位</legend>
    <form method="post" action="/mfg/control/locationSave">
        <p>
            库位编码 <input type="text" name="locationId" size="14" placeholder="如 WH1-A-03"/>
            名称 <input type="text" name="locationName" size="16"/>
            类型
            <select name="locationTypeId">
                <option value="MFG_LOC_BIN">架 / 位</option>
                <option value="MFG_LOC_ZONE">区</option>
                <option value="MFG_LOC_WAREHOUSE">仓</option>
            </select>
            上级 <input type="text" name="parentLocationId" size="12" placeholder="可选"/>
            备注 <input type="text" name="note" size="16"/>
            <input type="submit" value="保存"/>
        </p>
        <p class="hint">编码就是贴在货架上的那个号；同一个编码再保存一次就是改名/改类型。</p>
    </form>
</fieldset>

<fieldset>
    <legend>移库</legend>
    <form method="post" action="/mfg/control/stockTransfer">
        <p>
            批次 <input type="text" name="lotId" size="16" placeholder="LOT-… / FG-WO-…"/>
            从 <input type="text" name="fromLocationId" size="12" placeholder="留空 = 未分配"/>
            到 <input type="text" name="toLocationId" size="12" placeholder="库位编码"/>
            数量 <input type="text" name="qty" size="6"/>
            操作人 <input type="text" name="operatorName" size="8"/>
            备注 <input type="text" name="note" size="14"/>
            <input type="submit" value="移库"/>
        </p>
        <p class="hint">移库写两条流水（出 + 入），批次总量不变，移库单号 <code>TR-yyMMdd-NN</code>；
            来源库位的余额不够会被拒绝。老批次的货在「未分配库位」里，也可以从这里归位。</p>
    </form>
</fieldset>

<h2>库位</h2>
<#if locations?has_content>
    <table id="locationTable">
        <tr>
            <th>库位</th><th>名称</th><th>类型</th><th>上级</th>
            <th class="num">在库</th><th class="num">批次数</th><th>备注</th><th>操作</th>
        </tr>
        <#list locations as loc>
            <tr>
                <td>${loc.locationId}</td>
                <td>${loc.locationName!}</td>
                <td>${locLabel(loc.locationTypeId)}</td>
                <td>${loc.parentLocationId!}</td>
                <td class="num">${(loc.onHandQty!0)?string("0.##")}</td>
                <td class="num">${(loc.lotCount)!0}</td>
                <td>${loc.note!}</td>
                <td><a href="/mfg/control/locations?locationId=${loc.locationId?url}">只看这里</a></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有库位。先在上面建一个（例如 <code>WH1-A-03</code>），再到货时把批次放进这个库位。</p>
</#if>

<h2>批次 × 库位结存</h2>
<form method="get" action="/mfg/control/locations">
    库位 <input type="text" name="locationId" size="12" value="${(ls.locationId)!''}"/>
    料号 <input type="text" name="componentCode" size="14" value="${(ls.componentCode)!''}"/>
    <input type="submit" value="查询"/>
    <a href="/mfg/control/locations">清除</a>
    <a href="/mfg/control/stock">返回结存</a>
</form>
<#if rows?has_content>
    <table id="locationStockTable">
        <tr><th>料号</th><th>名称</th><th>类别</th><th>批次</th><th>库位</th>
            <th class="num">在库</th><th>到货日</th></tr>
        <#list rows as row>
            <tr>
                <td>${row.componentCode!}</td>
                <td>${row.componentName!}</td>
                <td><#if (row.lotKindId!'') == 'MFG_LOT_PRODUCT'>成品<#else>元件</#if></td>
                <td><a href="/mfg/control/stockMoves?lotId=${row.lotId?url}">${row.lotId}</a></td>
                <td><#if row.locationId??>${row.locationId}<#else><span class="warn">未分配</span></#if></td>
                <td class="num">${(row.onHandQty!0)?string("0.##")}</td>
                <td><#if row.receivedDate??>${row.receivedDate?string("yyyy-MM-dd")}</#if></td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">这个条件下列表是空的——换个库位或料号试试。</p>
</#if>

<h2>最近移库</h2>
<#if transfers?has_content>
    <table id="transferTable">
        <tr><th>移库单</th><th>时间</th><th>批次</th><th class="num">数量</th>
            <th>库位</th><th>操作人</th><th>备注</th></tr>
        <#list transfers as move>
            <tr>
                <td>${move.referenceNo!}</td>
                <td><#if move.effectiveDate??>${move.effectiveDate?string("MM-dd HH:mm")}</#if></td>
                <td><a href="/mfg/control/stockMoves?lotId=${move.lotId?url}">${move.lotId}</a></td>
                <td class="num"><#if ((move.displayQty)!0) lt 0><span class="error">${move.displayQty?string("0.##")}</span><#else><span class="ok">+${move.displayQty?string("0.##")}</span></#if></td>
                <td><#if move.locationId??>${move.locationId}<#else><span class="warn">未分配</span></#if></td>
                <td>${move.operatorName!}</td>
                <td>${move.note!}</td>
            </tr>
        </#list>
    </table>
    <p class="hint">一次移库写两条流水（一出一入），所以列表里同一个移库单会出现两行；批次总量不变。</p>
<#else>
    <p class="hint">还没有移库记录。</p>
</#if>
