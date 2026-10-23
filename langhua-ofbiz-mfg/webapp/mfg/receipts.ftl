<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  Purchase receipts: one purchase (a Taobao order, a supplier invoice) with the lots it brought in
  and the freight paid for it. The freight stays a period cost, so the part price is the price
  really paid for the part.
  Data: receiptList.receipts and its totals, receiptDetail.receipt / lines / freeLots
-->
<#assign rl = (receiptList)!{}>
<#assign receipts = (rl.receipts)![]>
<#assign rd = (receiptDetail)!{}>
<#assign receipt = (rd.receipt)!{}>
<#assign lines = (rd.lines)![]>
<#assign freeLots = (rd.freeLots)![]>
<#assign errList = (errorMessageList)![]>

<h1>采购到货单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<h2>采购支出（本页列出的单据）</h2>
<table id="receiptSummary" data-allocation="MFG_ALLOC_NONE">
    <tr><th>货款合计</th><th>运费（期间费用）</th><th>采购支出合计</th><th>运费占货款</th></tr>
    <tr>
        <td class="num">${((rl.goodsTotal)!0)?string("0.00")}</td>
        <td class="num">${((rl.freightTotal)!0)?string("0.00")}</td>
        <td class="num"><b>${((rl.spendTotal)!0)?string("0.00")}</b></td>
        <td class="num"><#if ((rl.freightTotal)!0) gt 0>${((rl.freightShare)!0)?string("0.0")}%<#else><span class="hint">—</span></#if></td>
    </tr>
</table>
<p class="hint">运费记在<strong>整张单</strong>上，按期间费用处理，<strong>不摊进料价</strong>：一个淘宝单里混着买电阻和芯片时，
按数量平均会把小件单价抬得很高、大件几乎不承担运费；按金额平均又会让贵料的成本跟着运费走。
要看“含运成本”就用上面的比例（运费占货款 ${((rl.freightShare)!0)?string("0.0")}%）整体估算，料价本身仍用实付单价。</p>

<h2>到货单（${(rl.totalCount)!0} 张）</h2>
<form method="get" action="/mfg/control/receipts">
    搜索 <input type="text" name="search" size="24" value="${(rl.search)!''}" placeholder="单号 / 供应商 / 店铺订单号"/>
    <input type="submit" value="查询"/>
</form>
<#if receipts?has_content>
    <table id="receiptTable">
        <tr><th>单号</th><th>供应商</th><th>店铺订单号</th><th>到货日</th>
            <th class="num">行数</th><th class="num">货款</th><th class="num">运费</th><th class="num">合计</th></tr>
        <#list receipts as r>
            <tr data-receipt="${r.receiptId}">
                <td><a href="/mfg/control/receipts?receiptId=${r.receiptId}">${r.receiptId}</a></td>
                <td>${r.supplierName!}</td>
                <td>${r.supplierOrderNo!}</td>
                <td><#if r.receiptDate??>${r.receiptDate?string("yyyy-MM-dd")}</#if></td>
                <td class="num">${(r.lineCount)!0}</td>
                <td class="num">${((r.goodsTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.freightTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.spendTotal)!0)?string("0.00")}</td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有采购到货单。建一张单，把这次买到的批次挂上去，运费的账就有地方记了。</p>
</#if>
<#if (rl.pageCount?default(1)) gt 1>
    <p class="pager">
        第 ${(rl.page)!1} / ${(rl.pageCount)!1} 页（共 ${(rl.totalCount)!0} 张）
        <#if (rl.page?default(1)) gt 1>
            <a href="/mfg/control/receipts?search=${(rl.search)!''?url}&page=${(rl.page)-1}">上一页</a>
        </#if>
        <#if (rl.page?default(1)) lt (rl.pageCount?default(1))>
            <a href="/mfg/control/receipts?search=${(rl.search)!''?url}&page=${(rl.page)+1}">下一页</a>
        </#if>
    </p>
</#if>

<fieldset id="orderBlock">
    <legend>从淘宝订单建单并入库（一行一个料，人工核对后提交）</legend>
    <p>
        截图识别
        <input type="file" id="orderImage" name="image" accept="image/png,image/jpeg"/>
        <button type="button" id="scanOrder">识别并填入</button>
        <span id="scanHint" class="hint">需要先在实例上配置识图服务；没配就把明细粘到下面。</span>
    </p>
    <form method="post" action="/mfg/control/receiptFromOrder">
        <p>
            订单号 <input type="text" id="orderNoField" name="supplierOrderNo" size="22" placeholder="淘宝订单号"/>
            店铺 <input type="text" id="shopField" name="supplierName" size="18" placeholder="天猫 risym旗舰店"/>
            到货日 <input type="date" id="dateField" name="receiptDate" title="留空就是今天"/>
            运费 <input class="narrow" type="text" id="freightField" name="freightAmount" value="0" title="整单运费；截图上写“含运费 ¥0.00”就填 0"/>
        </p>
        <p>
            <textarea id="linesField" name="lines" rows="4" cols="80" placeholder="料号,名称,数量,实付金额&#10;CAP-104-50V,瓷片电容器 104 0.1uF 100NF 50V,100,2.91">料号,名称,数量,实付金额
</textarea>
        </p>
        <p>
            <input type="submit" value="建单并入库"/>
            <span class="hint">数量按<strong>个</strong>填（截图“拍1件=100个”就是 100），金额填这一行的<strong>实付</strong>；
                单价由 金额 ÷ 数量 自动算（0.0291 这种不会被舍成 0.03）。同一订单号再提交一次不会重复入库。
                <strong>料号原样保存</strong>（最长 100 个字符，像 <code>QFN16-0.65-QFN20-0.4-2.54</code> 这种也放得下）。</span>
        </p>
    </form>
    <script>
    /* recognition is a help: it only fills the form, the person still checks and submits */
    (function () {
        var button = document.getElementById('scanOrder');
        var file = document.getElementById('orderImage');
        var hint = document.getElementById('scanHint');
        if (!button || !file || !hint || !window.fetch || !window.FormData) {
            return;
        }
        button.addEventListener('click', function () {
            if (!file.files || file.files.length === 0) {
                hint.className = 'error';
                hint.textContent = '先选一张订单截图';
                return;
            }
            var body = new FormData();
            body.append('image', file.files[0]);
            hint.className = 'hint';
            hint.textContent = '正在识别…';
            fetch('/mfg/control/orderImage', { method: 'POST', body: body, credentials: 'same-origin' })
                .then(function (answer) { return answer.json(); })
                .then(function (data) {
                    if (!data.ok) {
                        hint.className = 'error';
                        hint.textContent = data.message || '识别失败';
                        return;
                    }
                    fill('orderNoField', data.orderNo);
                    fill('shopField', data.shop);
                    fill('dateField', data.date);
                    if (data.freight) { fill('freightField', data.freight); }
                    if (data.lines) { fill('linesField', data.lines); }
                    hint.className = 'ok';
                    hint.textContent = '已识别，核对后点“建单并入库”';
                })
                .catch(function () {
                    hint.className = 'error';
                    hint.textContent = '识别请求失败，手工填也可以';
                });
        });
        function fill(id, value) {
            if (!value) { return; }
            var field = document.getElementById(id);
            if (field) { field.value = value; }
        }
    }());
    </script>
</fieldset>

<fieldset>
    <legend>新建到货单</legend>
    <form method="post" action="/mfg/control/receiptSave">
        <p>
            供应商 <input type="text" name="supplierName" size="18" placeholder="淘宝店铺"/>
            店铺订单号 <input type="text" name="supplierOrderNo" size="20" placeholder="淘宝订单号（可空）"/>
            到货日 <input type="date" name="receiptDate" title="留空就是今天"/>
            运费 <input class="narrow" type="text" name="freightAmount" value="0" title="整单运费，记期间费用"/>
            备注 <input type="text" name="note" size="20"/>
            <input type="submit" value="建单"/>
        </p>
    </form>
</fieldset>

<#if receipt.receiptId??>
    <h2>到货单 ${receipt.receiptId}（${(rd.lineCount)!0} 行）</h2>
    <form method="post" action="/mfg/control/receiptSave">
        <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
        <p>
            供应商 <input type="text" name="supplierName" size="18" value="${receipt.supplierName!''}"/>
            店铺订单号 <input type="text" name="supplierOrderNo" size="20" value="${receipt.supplierOrderNo!''}"/>
            到货日 <input type="date" name="receiptDate" value="<#if receipt.receiptDate??>${receipt.receiptDate?string("yyyy-MM-dd")}</#if>"/>
            运费 <input class="narrow" type="text" name="freightAmount" value="${((receipt.freightAmount)!0)?string("0.00")}"/>
            备注 <input type="text" name="note" size="20" value="${receipt.note!''}"/>
            <input type="submit" value="保存单据"/>
        </p>
    </form>

    <#if lines?has_content>
        <table id="receiptLines">
            <tr><th class="num">行号</th><th>批次</th><th>料号</th><th>名称</th>
                <th class="num">数量</th><th class="num">单价</th><th class="num">货款</th><th>操作</th></tr>
            <#list lines as l>
                <tr data-line-seq="${l.lineSeq}">
                    <td class="num">${(l.lineSeq)?string("0")}</td>
                    <td><a href="/mfg/control/materials?componentCode=${l.componentCode!''?url}">${l.lotId!}</a></td>
                    <td>${l.componentCode!}</td>
                    <td>${l.componentName!}</td>
                    <td class="num">${((l.qty)!0)?string("0.##")}</td>
                    <td class="num" title="单价 = 实付金额 ÷ 数量">${((l.unitPriceExact)!0)?string("0.0000")}</td>
                    <td class="num" data-amount="${((l.goodsAmount)!0)}">${((l.goodsAmount)!0)?string("0.00")}</td>
                    <td>
                        <form method="post" action="/mfg/control/receiptLineSave" class="inline">
                            <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
                            <input type="hidden" name="lineSeq" value="${l.lineSeq}"/>
                            <input type="hidden" name="delete" value="Y"/>
                            <input type="submit" value="移出此单"/>
                        </form>
                    </td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">这张单还没有挂批次，在下面选一个这次到货的批次。</p>
    </#if>

    <fieldset>
        <legend>挂一个到货批次到这张单</legend>
        <#if freeLots?has_content>
            <form method="post" action="/mfg/control/receiptLineSave">
                <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
                <p>
                    批次
                    <select name="lotId" class="compact">
                        <#list freeLots as f>
                            <option value="${f.lotId}">${f.lotId} · ${f.componentCode!''} · ${((f.receivedQty)!0)?string("0.##")} 个 @ ${((f.unitPrice)!0)?string("0.000")}</option>
                        </#list>
                    </select>
                    数量 <input class="narrow" type="text" name="qty" placeholder="默认批次数量"/>
                    单价 <input class="narrow" type="text" name="unitPrice" placeholder="默认批次单价"/>
                    <input type="submit" value="挂到本单"/>
                </p>
            </form>
        <#else>
            <p class="hint">所有元件批次都已经挂在到货单上了。新的到货批次在「物料批次」页建。</p>
        </#if>
    </fieldset>
</#if>
