<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#import "pager.ftl" as pg>
<#--
  Purchase receipts: one purchase (a Taobao order, a supplier invoice) with the lots it brought in
  and the freight paid for it. The freight stays a period cost, so the part price is the price
  really paid for the part.
  Data: receiptList.receipts and its totals, receiptDetail.receipt / lines / freeLots
-->
<#assign rl = (receiptList)!{}>
<#assign receipts = (rl.receipts)![]>
<#assign linesByReceipt = (rl.linesByReceipt)!{}>
<#assign rd = (receiptDetail)!{}>
<#assign receipt = (rd.receipt)!{}>
<#assign lines = (rd.lines)![]>
<#assign freeLots = (rd.freeLots)![]>
<#assign errList = (errorMessageList)![]>

<h1>采购到货单</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p id="listHint" class="hint">展开某张单就能直接改数量与单价，点「保存」立刻生效，页面不刷新。</p>
<div id="receiptListBlock">
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
                <#assign detail = (linesByReceipt[r.receiptId])![]>                <td class="num"><#if detail?has_content><a href="#" class="toggle"
                        data-toggle-receipt="${r.receiptId}" data-expanded="N"
                        title="展开这张单的明细">${(r.lineCount)!0} ▸</a><#else>${(r.lineCount)!0}</#if></td>
                <td class="num">${((r.goodsTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.freightTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.spendTotal)!0)?string("0.00")}</td>
            </tr>
            <#if detail?has_content>
                <tr class="receiptLines" data-receipt-lines="${r.receiptId}" hidden>
                    <td colspan="8">
                        <table class="inner">
                            <tr><th>行号</th><th>批次</th><th>料号</th><th>名称</th>
                                <th class="num">数量</th><th class="num">单价</th><th class="num">货款</th><th></th></tr>
                            <#list detail as l>
                                <#assign lineForm = "receiptLine_" + r.receiptId + "_" + (l.lineSeq)!0>
                                <tr data-receipt-line-seq="${(l.lineSeq)!0}">
                                    <td class="num">${(l.lineSeq)!0}</td>
                                    <td><a href="/mfg/control/materials?search=${(l.lotId)!''?url}">${l.lotId!}</a></td>
                                    <td>${l.componentCode!}</td>
                                    <td>${l.componentName!}</td>
                                    <td class="num"><input class="narrow" type="text" name="qty"
                                            form="${lineForm}" value="${(l.qty)!0}"/></td>
                                    <td class="num"><input class="narrow" type="text" name="unitPrice"
                                            form="${lineForm}" value="${((l.unitPrice)!0)?string("0.0000")}"/></td>
                                    <td class="num" data-goods-amount="${((l.goodsAmount)!0)?string("0.00")}">${((l.goodsAmount)!0)?string("0.00")}</td>
                                    <td class="num"><button type="submit" form="${lineForm}"
                                            title="保存这一行的数量与单价">保存</button></td>
                                </tr>
                            </#list>
                        </table>
                    </td>
                </tr>
            </#if>
        </#list>
    </table>
    <#-- the forms the cells of a line are bound to; a form may not sit inside a table -->
    <#list receipts as r>
        <#list (linesByReceipt[r.receiptId])![] as l>
            <form id="receiptLine_${r.receiptId}_${(l.lineSeq)!0}" method="post"
                    action="/mfg/control/receiptLineSave" data-receipt-line="${r.receiptId}">
                <input type="hidden" name="receiptId" value="${r.receiptId}"/>
                <input type="hidden" name="lineSeq" value="${(l.lineSeq)!0}"/>
            </form>
        </#list>
    </#list>
<#else>
    <p class="hint">还没有采购到货单。建一张单，把这次买到的批次挂上去，运费的账就有地方记了。</p>
</#if>
<#assign pagerQuery = ''><#if (rl.search)?has_content><#assign pagerQuery = 'search=' + rl.search?url></#if>
<@pg.pager action="/mfg/control/receipts" query=pagerQuery
    page=(rl.page)!1 pageCount=(rl.pageCount)!1 total=(rl.totalCount)!0 unit="张" pageSize=(rl.pageSize)!20/>
</div>

<script>
/* the lines of a receipt open under its row, and a line can be corrected in place: the list block
   is fetched again after a save, so every total follows without a full page load (Step 76) */
(function () {
    function openLines(ids) {
        ids.forEach(function (id) {
            var row = document.querySelector('[data-receipt-lines="' + id + '"]');
            var link = document.querySelector('[data-toggle-receipt="' + id + '"]');
            if (row) { row.hidden = false; }
            if (link) {
                link.setAttribute('data-expanded', 'Y');
                link.textContent = link.textContent.replace(/[▸▾]/g, '▾');
            }
        });
    }
    function hint(text, kind) {
        var line = document.getElementById('listHint');
        if (line) {
            line.className = kind;
            line.textContent = text;
        }
    }
    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('[data-toggle-receipt]') : null;
        if (!link) {
            return;
        }
        event.preventDefault();
        var id = link.getAttribute('data-toggle-receipt');
        var row = document.querySelector('[data-receipt-lines="' + id + '"]');
        if (!row) {
            return;
        }
        var opened = !row.hidden;
        row.hidden = opened;
        link.setAttribute('data-expanded', opened ? 'N' : 'Y');
        link.textContent = link.textContent.replace(/[▸▾]/g, opened ? '▸' : '▾');
    });
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.hasAttribute || !form.hasAttribute('data-receipt-line')) {
            return;
        }
        event.preventDefault();
        hint('正在保存…', 'hint');
        fetch('/mfg/control/receiptLineJson', {
            method: 'POST',
            body: new URLSearchParams(new FormData(form)),
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }
        })
            .then(function (answer) { return answer.json(); })
            .then(function (data) {
                if (!data.ok) {
                    hint(data.message || '保存失败', 'error');
                    return;
                }
                var open = [];
                Array.prototype.forEach.call(document.querySelectorAll('[data-receipt-lines]'), function (row) {
                    if (!row.hidden) { open.push(row.getAttribute('data-receipt-lines')); }
                });
                fetch(location.href, { credentials: 'same-origin' })
                    .then(function (answer) { return answer.text(); })
                    .then(function (html) {
                        var fresh = new DOMParser().parseFromString(html, 'text/html')
                            .getElementById('receiptListBlock');
                        var block = document.getElementById('receiptListBlock');
                        if (!fresh || !block) {
                            location.reload();
                            return;
                        }
                        block.innerHTML = fresh.innerHTML;
                        openLines(open);
                        hint(data.message || '已保存', 'ok');
                    });
            })
            .catch(function () {
                /* the script route did not answer: send the plain form so nothing is lost */
                form.submit();
            });
    });
}());
</script>

<fieldset id="orderBlock">
    <legend>从淘宝订单建单并入库（一行一个料，人工核对后提交）</legend>
    <p>
        截图识别
        <input type="file" id="orderImage" name="image" accept="image/png,image/jpeg"/>
        <button type="button" id="scanOrder">识别并填入</button>
        <span id="scanHint" class="hint">选文件后点按钮，或者直接 <kbd>Ctrl+V</kbd> 粘贴截图（也可以把图拖进来）；
            没配识图服务就把明细粘到下面。</span>
    </p>
    <form method="post" action="/mfg/control/receiptFromOrder">
        <p>
            订单号 <input type="text" id="orderNoField" name="supplierOrderNo" size="22" placeholder="淘宝订单号"/>
            店铺 <input type="text" id="shopField" name="supplierName" size="18" placeholder="天猫 risym旗舰店"/>
            到货日 <input type="date" id="dateField" name="receiptDate" title="收货那天，不是下单日；留空就是今天"/>
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
        var block = document.getElementById('orderBlock');
        if (!button || !file || !hint || !window.fetch || !window.FormData) {
            return;
        }
        /* the screenshot may come from the picker, from Ctrl+V or from a drop; all three end up
           as the file of the input, which the button then sends as before */
        var chosen = null;
        function take(fileLike, source) {
            if (!fileLike || !/^image\//.test(fileLike.type)) {
                return false;
            }
            chosen = fileLike;
            try {
                var carrier = new DataTransfer();
                carrier.items.add(fileLike);
                file.files = carrier.files;
            } catch (e) {
                // an older browser refuses a scripted FileList, the button uses `chosen` anyway
            }
            hint.className = 'ok';
            hint.textContent = source + '：' + (fileLike.name || '截图') + '，点“识别并填入”';
            return true;
        }
        function pasted(event) {
            var items = (event.clipboardData && event.clipboardData.items) || [];
            for (var i = 0; i < items.length; i++) {
                if (items[i].kind === 'file' && /^image\//.test(items[i].type)) {
                    return items[i].getAsFile();
                }
            }
            return null;
        }
        /* a paste that carries text belongs to the box the person is typing in, only an image
           alone is taken as the screenshot */
        function carriesText(event) {
            var data = event.clipboardData;
            return !!(data && data.getData && data.types
                && Array.prototype.indexOf.call(data.types, 'text/plain') >= 0
                && data.getData('text/plain'));
        }
        document.addEventListener('paste', function (event) {
            var image = pasted(event);
            if (!image || carriesText(event)) {
                return;
            }
            event.preventDefault();
            take(image, '已粘贴截图');
            if (block && block.scrollIntoView) { block.scrollIntoView({ block: 'center' }); }
        });
        file.addEventListener('change', function () {
            if (file.files && file.files.length) { take(file.files[0], '已选文件'); }
        });
        if (block) {
            block.addEventListener('dragover', function (event) {
                event.preventDefault();
                block.classList.add('drop');
            });
            block.addEventListener('dragleave', function () { block.classList.remove('drop'); });
            block.addEventListener('drop', function (event) {
                event.preventDefault();
                block.classList.remove('drop');
                var dropped = event.dataTransfer && event.dataTransfer.files;
                if (dropped && dropped.length) { take(dropped[0], '已拖入截图'); }
            });
        }
        button.addEventListener('click', function () {
            var image = chosen || (file.files && file.files[0]);
            if (!image) {
                hint.className = 'error';
                hint.textContent = '先选一张截图、Ctrl+V 粘贴，或把图拖进来';
                return;
            }
            var body = new FormData();
            body.append('image', image);
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
                    if (data.freight) { fill('freightField', data.freight); }
                    if (data.lines) { fill('linesField', data.lines); }
                    /* the date on a shop order is the day it was placed; the arrival day is a few
                       days later and the person picks it, so the field is left alone */
                    var text = data.message || '已识别，核对后点“建单并入库”';
                    text += data.orderDate
                        ? '。截图上的 ' + data.orderDate + ' 是下单日，到货日请自己选（一般下单后 3~4 天）'
                        : '。到货日请自己选';
                    hint.className = 'ok';
                    hint.textContent = text;
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
    <form method="post" action="/mfg/control/receiptSave" id="receiptSaveForm">
        <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
        <p>
            供应商 <input type="text" name="supplierName" size="18" value="${receipt.supplierName!''}"/>
            店铺订单号 <input type="text" name="supplierOrderNo" size="20" value="${receipt.supplierOrderNo!''}"/>
            到货日 <input type="date" name="receiptDate" value="<#if receipt.receiptDate??>${receipt.receiptDate?string("yyyy-MM-dd")}</#if>"/>
            运费 <input class="narrow" type="text" name="freightAmount" value="${((receipt.freightAmount)!0)?string("0.00")}"/>
            备注 <input type="text" name="note" size="20" value="${receipt.note!''}"/>
            <input type="submit" value="保存单据"/>
            <span id="saveHint" class="hint">改了哪项就点一下保存，页面不会刷新</span>
        </p>
    </form>
    <script>
    /* saves the head of the receipt without reloading the page; the plain form post stays as the
       fallback for a browser without fetch, and the fields keep what was just typed */
    (function () {
        var form = document.getElementById('receiptSaveForm');
        var hint = document.getElementById('saveHint');
        if (!form || !hint || !window.fetch || !window.FormData || !window.URLSearchParams) {
            return;
        }
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var body = new URLSearchParams(new FormData(form));
            hint.className = 'hint';
            hint.textContent = '正在保存…';
            fetch('/mfg/control/receiptSaveJson', {
                method: 'POST',
                body: body,
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }
            })
                .then(function (answer) { return answer.json(); })
                .then(function (data) {
                    hint.className = data.ok ? 'ok' : 'error';
                    hint.textContent = data.message || (data.ok ? '已保存' : '保存失败');
                })
                .catch(function () {
                    /* the script route did not answer: send the plain form so nothing is lost */
                    form.submit();
                });
        });
    }());
    </script>

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
