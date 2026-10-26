<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#import "pager.ftl" as pg>
<#--
  Purchase receipts: one purchase (a Taobao order, a supplier invoice) with the lots it brought in
  and the freight paid for it. The freight stays a period cost, so the part price is the price
  really paid for the part.
  Data: receiptList.receipts and its totals, receiptDetail.receipt / lines / freeLots and the
        refunded / closed shop orders of refundedList
-->
<#assign rl = (receiptList)!{}>
<#assign receipts = (rl.receipts)![]>
<#assign linesByReceipt = (rl.linesByReceipt)!{}>
<#assign rd = (receiptDetail)!{}>
<#assign receipt = (rd.receipt)!{}>
<#assign lines = (rd.lines)![]>
<#assign freeLots = (rd.freeLots)![]>
<#assign refundedOrders = (refundedList.refundedOrders)![]>
<#assign errList = (errorMessageList)![]>

<h1>采购到货单</h1>

<#if (parameters.updatedCount)??>
    <p class="ok">本次导入更新了 ${parameters.updatedCount} 行原有明细（数量与金额按这次的数字）。</p>
</#if>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<p id="listHint" class="hint">展开某张单就能直接改数量与单价，点「保存」立刻生效，页面不刷新；
    要作废整张单就点单号进详情（右上有个「作废这张到货单」，单据留痕、批次与库存不动）。</p>
<div id="receiptListBlock">
<h2>采购支出（本页列出的单据）</h2>
<table id="receiptSummary" data-allocation="MFG_ALLOC_NONE">
    <tr><th>货款合计</th><th>运费（期间费用）</th><th>优惠（红包 / 满减）</th><th>采购支出合计</th><th>运费占货款</th></tr>
    <tr>
        <td class="num">${((rl.goodsTotal)!0)?string("0.00")}</td>
        <td class="num">${((rl.freightTotal)!0)?string("0.00")}</td>
        <td class="num">${((rl.discountTotal)!0)?string("0.00")}</td>
        <td class="num"><b>${((rl.spendTotal)!0)?string("0.00")}</b></td>
        <td class="num"><#if ((rl.freightTotal)!0) gt 0>${((rl.freightShare)!0)?string("0.0")}%<#else><span class="hint">—</span></#if></td>
    </tr>
</table>
<p class="hint">运费记在<strong>整张单</strong>上，按期间费用处理，<strong>不摊进料价</strong>：一个淘宝单里混着买电阻和芯片时，
按数量平均会把小件单价抬得很高、大件几乎不承担运费；按金额平均又会让贵料的成本跟着运费走。
要看“含运成本”就用上面的比例（运费占货款 ${((rl.freightShare)!0)?string("0.0")}%）整体估算，料价本身仍用实付单价。</p>

<h2>到货单（${(rl.totalCount)!0} 张）</h2>
<#assign showVoid = ((parameters.includeVoid)!'') == 'Y'>
<form method="get" action="/mfg/control/receipts">
    搜索 <input type="text" name="search" size="24" value="${(rl.search)!''}" placeholder="单号 / 供应商 / 店铺订单号"/>
    <input type="submit" value="查询"/>
    <input type="hidden" name="includeVoid" value="<#if showVoid>Y<#else>N</#if>"/>
</form>
<p class="hint">作废的单不算钱也不占批次，默认不列出来：
    <#if showVoid><a href="/mfg/control/receipts?search=${(rl.search)!''?url}">只看有效的</a>（现在</#if><#if !showVoid><a href="/mfg/control/receipts?search=${(rl.search)!''?url}&amp;includeVoid=Y">连已作废的一起看</a></#if><#if showVoid>已把作废的也列出来）</#if>。
</p>
<#if receipts?has_content>
    <table id="receiptTable">
        <tr><th>单号</th><th>供应商</th><th>店铺订单号</th><th>到货日</th>
            <th class="num">行数</th><th class="num">货款</th><th class="num">运费</th><th class="num">优惠</th><th class="num">合计</th><th>状态</th></tr>
        <#list receipts as r>
            <#assign voided = ((r.statusId)!'') == 'MFG_RCPT_VOID'>
            <tr data-receipt="${r.receiptId}" data-receipt-status="${(r.statusId)!'MFG_RCPT_ACTIVE'}">
                <td><a href="/mfg/control/receipts?receiptId=${r.receiptId}#receiptDetail">${r.receiptId}</a></td>
                <td>${r.supplierName!}</td>
                <td>${r.supplierOrderNo!}</td>
                <td><#if r.receiptDate??>${r.receiptDate?string("yyyy-MM-dd")}</#if></td>
                <#assign detail = (linesByReceipt[r.receiptId])![]>                <td class="num"><#if detail?has_content><a href="#" class="toggle"
                        data-toggle-receipt="${r.receiptId}" data-expanded="N"
                        title="展开这张单的明细">${(r.lineCount)!0} ▸</a><#else>${(r.lineCount)!0}</#if></td>
                <td class="num">${((r.goodsTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.freightTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.discountTotal)!0)?string("0.00")}</td>
                <td class="num">${((r.spendTotal)!0)?string("0.00")}</td>
                <td<#if voided> class="error"</#if>><#if voided>已作废<#else>有效</#if></td>
            </tr>
            <#if detail?has_content>
                <tr class="receiptLines" data-receipt-lines="${r.receiptId}" hidden>
                    <td colspan="10">
                        <table class="inner">
                            <tr><th>行号</th><th>批次</th><th>料号</th><th>名称</th>
                                <th class="num">数量</th><th class="num">单价</th><th class="num">货款</th><th></th></tr>
                            <#list detail as l>
                                <#assign lineForm = "receiptLine_" + r.receiptId + "_" + (l.lineSeq)!0>
                                <#assign deleteForm = "receiptLineDel_" + r.receiptId + "_" + (l.lineSeq)!0>
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
                                            title="保存这一行的数量与单价">保存</button>
                                        <button type="submit" form="${deleteForm}" class="secondary"
                                            title="把这一行从这张单上删掉"
                                            onclick="return confirm('删除第 ${(l.lineSeq)!0} 行？只从这张到货单上删掉，批次和库存不动（要冲库存请到「库存 → 流水」按报废处理）')">删除</button></td>
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
            <form id="receiptLineDel_${r.receiptId}_${(l.lineSeq)!0}" method="post"
                    action="/mfg/control/receiptLineSave">
                <input type="hidden" name="receiptId" value="${r.receiptId}"/>
                <input type="hidden" name="lineSeq" value="${(l.lineSeq)!0}"/>
                <input type="hidden" name="delete" value="Y"/>
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

    /* an order that is already in must not be booked a second time without a word: the page asks
       before the same parts are overwritten (Step 77); a refunded order is kept out of the stock
       altogether (Step 84). The form sits BELOW this script, so the wiring waits for the document -
       looking it up here always found nothing. */
    function wireOrderForm() {
        var orderForm = document.getElementById('orderForm');
        if (!orderForm || !window.fetch || !window.FormData) {
            return;
        }
        orderForm.addEventListener('submit', function (event) {
            var orderNo = document.getElementById('orderNoField');
            var linesField = document.getElementById('linesField');
            if (!orderNo || !linesField || !orderNo.value.trim() || !linesField.value.trim()) {
                return;
            }
            event.preventDefault();
            var codes = linesField.value.split(/\r?\n/).map(function (row) {
                var cells = row.split(/\t|,/);
                return cells.length >= 4 ? cells[0].trim() : '';
            }).filter(function (code) {
                return code.length > 0;
            });
            fetch('/mfg/control/orderPeek?supplierOrderNo=' + encodeURIComponent(orderNo.value.trim()),
                { credentials: 'same-origin' })
                .then(function (answer) { return answer.json(); })
                .then(function (data) {
                    var already = (data.lines || []).filter(function (line) {
                        return codes.indexOf(line.componentCode) >= 0;
                    });
                    if (!already.length) {
                        orderForm.submit();
                        return;
                    }
                    var text = '这张单（' + orderNo.value.trim() + '）已经在库里了，其中有 '
                        + already.length + ' 个同样的料号：\n';
                    already.forEach(function (line) {
                        text += '  ' + line.componentCode + '：现有数量 ' + line.qty
                            + '，货款 ' + line.goodsAmount + '\n';
                    });
                    text += '\n要用这次明细里的数量与金额覆盖原有数据吗？（点“取消”就什么都不改）';
                    if (window.confirm(text)) {
                        document.getElementById('overwriteField').value = 'Y';
                        orderForm.submit();
                    } else {
                        hint('没有改动。核对数字后再点“建单并入库”，届时选“确定”即可覆盖原有明细。', 'hint');
                    }
                })
                .catch(function () {
                    orderForm.submit();
                });
        });
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', wireOrderForm);
    } else {
        wireOrderForm();
    }
}());
</script>

<fieldset id="orderBlock">
    <legend>从淘宝订单建单并入库（一行一个料，人工核对后提交）</legend>
    <p id="scanTools">
        截图识别
        <input type="file" id="orderImage" name="image" accept="image/png,image/jpeg"/>
        <button type="button" id="scanOrder" data-once="Y">识别并核对</button>
        <span id="scanHint" class="hint">选文件后点按钮，或者直接 <kbd>Ctrl+V</kbd> 粘贴截图（也可以把图拖进来）；
            整页的订单列表<b>不用缩</b>，长图会分段识别；
            识别结果先列在下面那张核对表里，确认后再点「导入这些单」。</span>
    </p>
    <div id="scanOrders" hidden="hidden">
        <p id="batchHint" class="hint"></p>
        <table class="inner" id="scanOrderTable">
            <tr>
                <th class="num">#</th>
                <th>订单号</th><th>店铺</th><th>下单</th><th class="num">运费</th><th class="num">优惠</th>
                <th class="num">明细</th><th class="num">货款</th><th class="num">实付</th><th>状态</th>
            </tr>
            <tbody id="scanOrderRows"></tbody>
        </table>
        <p>
            <button type="button" id="importOrders" data-once="Y">导入这些单</button>
            <span class="hint">一张截图里有几张单就建几张到货单（只有一张也一样）；已退款的单不入库，
                已经入过库的明细会跳过（不会重复）。哪一行读错了：点开「1 ▸」把明细、运费、实付改对，
                再点「保存这一行」，导入用的是改过的内容。</span>
        </p>
    </div>
    <p class="hint">识别不了、或者手里没有截图时，下面手工填也一样：一行一个料，提交后才写库。</p>
    <form method="post" action="/mfg/control/receiptFromOrder" id="orderForm">
        <input type="hidden" name="overwrite" id="overwriteField" value="N"/>
        <input type="hidden" name="refunded" id="refundField" value="N"/>
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
            <span id="orderHint" class="hint">数量按<strong>个</strong>填（截图“拍1件=100个”就是 100），金额填这一行的<strong>实付</strong>；
                单价由 金额 ÷ 数量 自动算（0.0291 这种不会被舍成 0.03）。同一订单号再提交一次不会重复入库。
                <strong>料号原样保存</strong>（最长 100 个字符，像 <code>QFN16-0.65-QFN20-0.4-2.54</code> 这种也放得下）。</span>
        </p>
    </form>
    <script>
    /* recognition is a help: it lists what it read, the person still checks and imports */
    (function () {
        var button = document.getElementById('scanOrder');
        var file = document.getElementById('orderImage');
        var hint = document.getElementById('scanHint');
        var block = document.getElementById('orderBlock');
        var tools = document.getElementById('scanTools');
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
            hint.textContent = source + '：' + (fileLike.name || '截图') + '，点「识别并核对」';
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
            /* aim at the box-and-button row, never the middle of the block: once it carries a long
               checklist the block is taller than the screen and centering hides the button
               (Step 115) */
            if (window.jumpUnderNav) { window.jumpUnderNav(tools || block); }
            else if (block && block.scrollIntoView) { block.scrollIntoView(); }
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
            /* recognition takes a few seconds, so the button stays dimmed (and unclickable) until
               the answer is in: once.js only covers the double click itself */
            var release = function () { button.className = button.className.replace(/\s*\bbusy\b/g, ''); };
            button.className = (button.className + ' busy').replace(/^\s+/, '');
            fetch('/mfg/control/orderImage', { method: 'POST', body: body, credentials: 'same-origin' })
                .then(jsonOrLogin)
                .then(function (data) {
                    if (!data.ok) {
                        hint.className = 'error';
                        hint.textContent = data.message || '识别失败';
                        /* the service already retried once; the person's second click is the next try */
                        button.textContent = '再试一次';
                        return;
                    }
                    button.textContent = '识别并核对';
                    /* one order is just a list of one: the table is always what comes back, so there
                       is only one way to go on (Step 90) */
                    if (data.orders && data.orders.length) {
                        showOrders(data.orders);
                        hint.className = 'ok';
                        hint.textContent = data.message || ('识别到 ' + data.orders.length + ' 张单');
                        return;
                    }
                    hint.className = 'error';
                    hint.textContent = data.message || '没有读出订单，再点一次，或者手工填';
                })
                .catch(function (problem) {
                    if (problem && problem.message === 'login') {
                        /* the page is already on its way to the login page */
                        return;
                    }
                    hint.className = 'error';
                    hint.textContent = '识别请求失败，再点一次，或者手工填';
                    button.textContent = '再试一次';
                })
                .then(release, release);
        });

        /* After a restart of the server the session is gone and the endpoint answers with the login
           page instead of JSON: jsonOrLogin (js/session.js) sends the person to the login page, and
           the promise is rejected with 'login' so no red "再点一次" message appears here. */

        /* a screenshot of the order list holds several orders: they are shown first, then booked in
           with one click (Step 86) */
        var orders = [];
        var ordersBox = document.getElementById('scanOrders');
        var orderRows = document.getElementById('scanOrderRows');
        var batchHint = document.getElementById('batchHint');
        var importButton = document.getElementById('importOrders');

        function linesOf(order) {
            return (order.lines || '').split(/\r?\n/).filter(function (row) {
                return row.trim().length > 0;
            });
        }
        /* the articles of one order, opened under its row: what exactly will be written, and the
           box to correct it - a block that came back with the articles of the block above it is fixed
           here instead of being typed again (Step 138) */
        function linesRowOf(order, key) {
            var rows = linesOf(order);
            var holder = document.createElement('tr');
            holder.setAttribute('data-lines', key);
            var cellHolder = document.createElement('td');
            cellHolder.colSpan = 10;
            var table = document.createElement('table');
            table.className = 'inner';
            var head = document.createElement('tr');
            ['料号', '名称', '数量', '金额'].forEach(function (title, index) {
                var th = document.createElement('th');
                th.textContent = title;
                if (index > 1) { th.className = 'num'; }
                head.appendChild(th);
            });
            table.appendChild(head);
            rows.forEach(function (line) {
                var cells = line.split(/\t|,/);
                var tr = document.createElement('tr');
                tr.setAttribute('data-scan-line', 'Y');
                var code = document.createElement('td');
                code.textContent = cells[0] || '';
                tr.appendChild(code);
                var name = document.createElement('td');
                name.textContent = cells.length >= 4 ? cells.slice(1, cells.length - 2).join(',') : '';
                tr.appendChild(name);
                var qty = document.createElement('td');
                qty.className = 'num';
                qty.textContent = cells.length >= 4 ? cells[cells.length - 2] : '';
                tr.appendChild(qty);
                var amount = document.createElement('td');
                amount.className = 'num';
                amount.textContent = cells.length >= 4 ? cells[cells.length - 1] : line;
                tr.appendChild(amount);
                table.appendChild(tr);
            });
            cellHolder.appendChild(table);
            var editor = document.createElement('p');
            var linesBox = document.createElement('textarea');
            linesBox.rows = Math.max(3, rows.length + 1);
            linesBox.cols = 78;
            linesBox.value = order.lines || '';
            linesBox.setAttribute('data-scan-lines', key);
            linesBox.title = '一行一个料：料号,名称,数量,金额（Tab 或逗号分隔）';
            editor.appendChild(linesBox);
            var moneyLine = document.createElement('span');
            moneyLine.appendChild(document.createTextNode(' 运费 '));
            moneyLine.appendChild(inputOf(key, 'freight', order.freight || '0', 5));
            moneyLine.appendChild(document.createTextNode(' 实付 '));
            moneyLine.appendChild(inputOf(key, 'paid', order.paidTotal || '', 7));
            var save = document.createElement('button');
            save.type = 'button';
            save.textContent = '保存这一行';
            save.setAttribute('data-scan-save', key);
            moneyLine.appendChild(save);
            var saved = document.createElement('span');
            saved.className = 'hint';
            saved.setAttribute('data-scan-saved', key);
            moneyLine.appendChild(saved);
            editor.appendChild(moneyLine);
            cellHolder.appendChild(editor);
            holder.appendChild(cellHolder);
            return holder;
        }
        function inputOf(key, what, value, size) {
            var input = document.createElement('input');
            input.type = 'text';
            input.size = size;
            input.value = value;
            input.setAttribute('data-scan-' + what, key);
            return input;
        }
        /* the row edited by hand: what it says is what the import writes */
        function saveRow(key) {
            var index = -1;
            for (var i = 0; i < orders.length; i++) {
                if ('scan-' + i === key) { index = i; }
            }
            if (index < 0) { return; }
            var order = orders[index];
            var box = orderRows.querySelector('[data-scan-lines="' + key + '"]');
            var freight = orderRows.querySelector('[data-scan-freight="' + key + '"]');
            var paid = orderRows.querySelector('[data-scan-paid="' + key + '"]');
            if (box) { order.lines = box.value.replace(/\r/g, ''); }
            if (freight) { order.freight = freight.value.trim() || '0'; }
            if (paid) { order.paidTotal = paid.value.trim(); }
            showOrders(orders);
            var again = orderRows.querySelector('[data-toggle-lines="' + key + '"]');
            if (again) { again.click(); }
            var hint = orderRows.querySelector('[data-scan-saved="' + key + '"]');
            if (hint) { hint.textContent = '已保存'; }
        }
        function cell(row, text, className) {
            var td = document.createElement('td');
            if (className) { td.className = className; }
            td.textContent = text;
            row.appendChild(td);
            return td;
        }
        function amountsOf(order) {
            var total = 0;
            linesOf(order).forEach(function (line) {
                var cells = line.split(/\t|,/);
                total += parseFloat(cells[cells.length - 1]) || 0;
            });
            return total;
        }
        /* a refunded order, or one whose deal never went through (交易关闭): neither is on the shelf,
           so neither is booked in (Step 108 / 109) */
        function leftOut(order) {
            return order.refunded === 'Y' || order.closed === 'Y';
        }
        function showOrders(found) {
            orders = found;
            if (!ordersBox || !orderRows) { return; }
            while (orderRows.firstChild) { orderRows.removeChild(orderRows.firstChild); }
            found.forEach(function (order, index) {
                var row = document.createElement('tr');
                row.setAttribute('data-scan-order', order.orderNo || '');
                row.setAttribute('data-platform', order.platform || '');
                var key = 'scan-' + index;
                /* the number of the block on this page: the screenshot can be held next to it, so a
                   block that was read twice or not at all is seen at once (Step 136) */
                var seq = cell(row, String(index + 1), 'num');
                seq.setAttribute('data-scan-seq', String(index + 1));
                cell(row, order.orderNo || '（没读到单号）');
                /* the platform is printed next to the shop, so a 京东 / 拼多多 / 抖音 order is not
                   mistaken for a Taobao one; the store itself stays untouched for the import */
                cell(row, order.platform ? (order.platform + ' · ' + (order.shop || '')) : (order.shop || ''));
                /* the date on a shop order is the day it was placed; the arrival day is a few days
                   later and the person picks it, so it is only shown here */
                var dateCell = cell(row, order.orderDate || '');
                dateCell.setAttribute('data-scan-date', order.orderDate || '');
                cell(row, order.freight || '0', 'num');
                cell(row, order.discount || '0', 'num');
                /* what will be written, article by article: the person must be able to read it
                   here, not only after the import (Step 93) */
                var countCell = cell(row, '', 'num');
                var link = document.createElement('a');
                link.href = '#';
                link.setAttribute('data-toggle-lines', key);
                link.setAttribute('data-open', 'N');
                link.appendChild(document.createTextNode(linesOf(order).length + ' '));
                var arrow = document.createElement('span');
                arrow.textContent = '▸';
                link.appendChild(arrow);
                countCell.appendChild(link);
                var goods = amountsOf(order);
                cell(row, goods.toFixed(2), 'num');
                cell(row, order.paidTotal || '', 'num');
                /* 货款 + 运费 − 优惠 must come to 实付: when it does not, an article is probably missing.
                   A page rounds its own unit prices (0.095 shows as 0.10), so the same few cents the
                   server allows are allowed here, or a row would be marked red for two cents */
                var paid = parseFloat(order.paidTotal);
                var pieces = 0;
                linesOf(order).forEach(function (line) {
                    var cells = line.split(/\t|,/);
                    pieces += Math.max(1, parseFloat(cells[cells.length - 2]) || 1);
                });
                var slack = Math.round((0.005 * pieces + 0.005) * 100) / 100;
                var off = !isNaN(paid) && goods > 0
                    && Math.abs(goods + (parseFloat(order.freight) || 0)
                        - (parseFloat(order.discount) || 0) - paid) > slack;
                /* 交易关闭 is not a refund: the deal never went through (usually never paid), so it is
                   left out as well - the two reasons are spelled out, because the person acts
                   differently on each (Step 109) */
                var kept = leftOut(order);
                var doubtful = !kept && !off && order.note;
                var statusText = order.refunded === 'Y' ? '已退款，不入库'
                    : (order.closed === 'Y' ? '交易关闭，不入库'
                        : (off ? '实付与明细对不上，请核对' : (order.note ? '请核对' : '待导入')));
                var status = cell(row, statusText);
                if (kept || off || doubtful) { status.className = 'error'; }
                if (order.note) { status.title = order.note; }
                orderRows.appendChild(row);
                orderRows.appendChild(linesRowOf(order, key));
            });
            ordersBox.hidden = false;
            var nothingToImport = found.length > 0 && found.every(leftOut);
            if (importButton) {
                /* nothing to import when every block is refunded or closed: say so instead of
                   letting the person press a button that only answers "not in the stock" */
                importButton.hidden = nothingToImport;
                importButton.textContent = found.length > 1 ? '导入这些单' : '导入这张单';
            }
            if (batchHint) {
                var notes = found.filter(function (order) {
                    return order.note;
                }).map(function (order) {
                    return (order.orderNo || '一张单') + '：' + order.note;
                });
                batchHint.className = nothingToImport ? 'error' : 'hint';
                if (nothingToImport) {
                    batchHint.textContent = '这张截图里的单都已退款或交易关闭，按规矩不入库；'
                        + '真收到货了就手工粘明细建单。';
                    return;
                }
                batchHint.textContent = (found.length > 1
                        ? '这张截图里共 ' + found.length + ' 张单，核对后点「导入这些单」。'
                        : '这张截图里就 1 张单，核对后点「导入这张单」。')
                    + (notes.length ? ' ' + notes.join('；') : '');
            }
        }
        /* the articles of one row are a draft until the person says they are right */
        orderRows.addEventListener('click', function (event) {
            var target = event.target;
            if (!target || !target.getAttribute) { return; }
            var key = target.getAttribute('data-scan-save');
            if (key) {
                event.preventDefault();
                saveRow(key);
            }
        });
        if (importButton) {
            importButton.addEventListener('click', function () {
                if (!orders.length) { return; }
                var params = new URLSearchParams();
                var wanted = [];
                orders.forEach(function (order) {
                    if (leftOut(order)) { return; }
                    var index = wanted.length;
                    wanted.push(order);
                    params.append('o' + index + '.orderNo', order.orderNo || '');
                    params.append('o' + index + '.shop', order.shop || '');
                    params.append('o' + index + '.freight', order.freight || '0');
                    params.append('o' + index + '.discount', order.discount || '0');
                    params.append('o' + index + '.lines', order.lines || '');
                });
                var skipped = orders.length - wanted.length;
                if (!wanted.length) {
                    if (batchHint) {
                        batchHint.className = 'error';
                        batchHint.textContent = '这几张单都是退款或交易关闭的，按规矩不入库。';
                    }
                    return;
                }
                params.append('count', String(wanted.length));
                importButton.className = (importButton.className + ' busy').replace(/^\s+/, '');
                if (batchHint) {
                    batchHint.className = 'hint';
                    batchHint.textContent = '正在导入 ' + wanted.length + ' 张单…';
                }
                fetch('/mfg/control/receiptFromOrdersJson', {
                    method: 'POST',
                    body: params,
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }
                })
                    .then(jsonOrLogin)
                    .then(function (data) {
                        var byOrder = {};
                        (data.results || []).forEach(function (result) {
                            byOrder[result.orderNo] = result;
                        });
                        var done = 0;
                        var already = 0;
                        Array.prototype.forEach.call(orderRows.querySelectorAll('tr'), function (row) {
                            var key = row.getAttribute('data-scan-order');
                            var result = byOrder[key];
                            var status = row.lastChild;
                            if (!result) { return; }
                            if (result.ok && result.new > 0) { done++; }
                            if (result.ok && !(result.new > 0)) { already++; }
                            status.className = result.ok ? 'ok' : 'error';
                            status.textContent = result.message || (result.ok ? '已入库' : '未入库');
                        });
                        if (batchHint) {
                            batchHint.className = (done + already) === wanted.length ? 'ok' : 'error';
                            batchHint.textContent = '导入完成：' + done + ' 张入库'
                                + (already > 0 ? '，' + already + ' 张之前已入过（这次没新增）' : '')
                                + (skipped > 0 ? '，' + skipped + ' 张退款或交易关闭的单按规矩没入' : '')
                                + ((done + already) < wanted.length ? '，其余见每行的原因' : '') + '。';
                        }
                        importButton.className = importButton.className.replace(/\s*\bbusy\b/g, '');
                        // the new receipts must show up in the list above; that list may be taller or
                        // shorter afterwards, so keep the checklist the person is reading in place
                        var anchorBefore = batchHint ? batchHint.getBoundingClientRect().top : 0;
                        fetch(location.href, { credentials: 'same-origin' })
                            .then(function (answer) { return answer.text(); })
                            .then(function (html) {
                                var fresh = new DOMParser().parseFromString(html, 'text/html')
                                    .getElementById('receiptListBlock');
                                var current = document.getElementById('receiptListBlock');
                                if (fresh && current) { current.innerHTML = fresh.innerHTML; }
                                if (batchHint) {
                                    window.scrollBy(0, batchHint.getBoundingClientRect().top - anchorBefore);
                                    /* the browser usually holds the anchor itself; line it up again if
                                       the list above grew enough to slide it under the nav */
                                    if (window.jumpUnderNav) { window.jumpUnderNav(batchHint); }
                                }
                            });
                    })
                    .catch(function (problem) {
                        importButton.className = importButton.className.replace(/\s*\bbusy\b/g, '');
                        if (problem && problem.message === 'login') {
                            return;
                        }
                        if (batchHint) {
                            batchHint.className = 'error';
                            batchHint.textContent = '导入请求失败了，刷新页面看看已经进去了几张。';
                        }
                    });
            });
        }
        /* the table is the only result now, so there is no form filling to clean up */
    }());
    </script>
</fieldset>

<fieldset id="excelBlock">
    <legend>从淘宝导出的「订单数据」Excel 一次导入（一张单一张到货单）</legend>
    <form method="post" action="/mfg/control/orderExcel" enctype="multipart/form-data" id="excelForm">
        <p id="excelTools">
            文件 <input type="file" id="excelFile" name="file" accept=".xlsx"/>
            <button type="submit" id="excelImport" data-once="Y">导入 Excel</button>
            <span id="excelHint" class="hint">淘宝「已买到的宝贝 → 导出」得到的 xlsx（1-10 页）；
                也可以直接 <kbd>Ctrl+V</kbd> 粘贴这个文件，或者把它拖到这个框里；
                料号取「型号款式」，没有就用商品名称；到货日用「订单提交时间」那天。</span>
        </p>
    </form>
    <div id="excelResult" hidden="hidden">
        <table class="inner" id="excelNoteTable">
            <tr><th>订单号</th><th>为什么没入库</th></tr>
            <tbody id="excelNoteRows"></tbody>
        </table>
    </div>
    <#if (parameters.excelImported)?has_content>
        <p class="ok">上次导入：写入 ${parameters.excelImported} 张单（${(parameters.excelLines)!'0'} 行），
            跳过 ${(parameters.excelSkipped)!'0'} 张，失败 ${(parameters.excelFailed)!'0'} 张。</p>
    </#if>
</fieldset>
<script>
(function () {
    var form = document.getElementById('excelForm');
    if (!form) { return; }
    var block = document.getElementById('excelBlock');
    var tools = document.getElementById('excelTools');
    var fileField = document.getElementById('excelFile');
    var hint = document.getElementById('excelHint');
    var button = document.getElementById('excelImport');
    var result = document.getElementById('excelResult');
    var rows = document.getElementById('excelNoteRows');
    function say(text, style) {
        hint.className = style || 'hint';
        hint.textContent = text;
    }
    /* the workbook may come from the picker, from Ctrl+V or from a drop; all three end up as the
       file of the input, which the form then sends exactly as before (Step 114) */
    var chosen = null;
    function workbookish(fileLike) {
        var name = (fileLike && fileLike.name ? fileLike.name : '').toLowerCase();
        var type = (fileLike && fileLike.type ? fileLike.type : '').toLowerCase();
        return name.slice(-5) === '.xlsx' || type.indexOf('spreadsheetml') >= 0
            || type === 'application/zip' || type === 'application/x-zip-compressed';
    }
    function take(fileLike, source) {
        if (!workbookish(fileLike)) { return false; }
        chosen = fileLike;
        try {
            var carrier = new DataTransfer();
            carrier.items.add(fileLike);
            fileField.files = carrier.files;
        } catch (e) {
            // an older browser refuses a scripted FileList, the form uses `chosen` anyway
        }
        say(source + '：' + (fileLike.name || 'xlsx') + '，点「导入 Excel」', 'ok');
        return true;
    }
    /* a pasted screenshot belongs to the scanner above, so only a workbook is taken here */
    function pastedFile(event) {
        var data = event.clipboardData;
        if (!data) { return null; }
        var files = data.files || [];
        for (var i = 0; i < files.length; i++) {
            if (workbookish(files[i])) { return files[i]; }
        }
        var items = data.items || [];
        for (var j = 0; j < items.length; j++) {
            if (items[j].kind !== 'file') { continue; }
            var got = items[j].getAsFile();
            if (got && workbookish(got)) { return got; }
        }
        return null;
    }
    document.addEventListener('paste', function (event) {
        var workbook = pastedFile(event);
        if (!workbook) { return; }
        event.preventDefault();
        take(workbook, '已粘贴文件');
        /* same rule as the screenshot block: line the file box up under the nav, even when the
           block below it carries a long list of orders that could not be booked in (Step 115) */
        if (window.jumpUnderNav) { window.jumpUnderNav(tools || block); }
        else if (block && block.scrollIntoView) { block.scrollIntoView(); }
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
            var dropped = (event.dataTransfer && event.dataTransfer.files) || [];
            if (!dropped.length) { return; }
            if (!take(dropped[0], '已拖入文件')) {
                say('只认淘宝导出的 xlsx（' + (dropped[0].name || '这个文件') + ' 不是）；'
                    + '截图请拖到上面的识别框', 'error');
            }
        });
    }
    form.addEventListener('submit', function (event) {
        if (!window.FormData || !window.fetch) { return; }
        event.preventDefault();
        var data = new FormData(form);
        if (chosen && !(fileField.files && fileField.files.length)) {
            try { data.set('file', chosen, chosen.name || 'orders.xlsx'); }
            catch (e) { data.append('file', chosen); }
        }
        data.append('ajax', 'Y');
        say('正在读文件，一张单一张到货单，请稍等…', 'hint');
        fetch('/mfg/control/orderExcel', { method: 'POST', body: data, credentials: 'same-origin' })
            .then(jsonOrLogin)
            .then(function (answer) {
                say(answer.message || (answer.ok ? '导入完成' : '没有导入'), answer.ok ? 'ok' : 'error');
                while (rows.firstChild) { rows.removeChild(rows.firstChild); }
                (answer.notes || []).forEach(function (note) {
                    var tr = document.createElement('tr');
                    tr.setAttribute('data-excel-note', note.orderNo || '');
                    var first = document.createElement('td');
                    first.textContent = note.orderNo || '';
                    var second = document.createElement('td');
                    second.textContent = note.message || '';
                    tr.appendChild(first);
                    tr.appendChild(second);
                    rows.appendChild(tr);
                });
                if (result) { result.hidden = !(answer.notes && answer.notes.length); }
                if (answer.imported > 0) {
                    /* the list below is re-read so the receipts just written show up without a reload;
                       that list is above this block, so it moves the file box and the note table -
                       keep the line the person is reading where it is */
                    var anchorBefore = hint ? hint.getBoundingClientRect().top : 0;
                    fetch(location.href).then(function (a) { return a.text(); }).then(function (html) {
                        var fresh = new DOMParser().parseFromString(html, 'text/html')
                            .getElementById('receiptListBlock');
                        var current = document.getElementById('receiptListBlock');
                        if (fresh && current) { current.innerHTML = fresh.innerHTML; }
                        if (hint) {
                            window.scrollBy(0, hint.getBoundingClientRect().top - anchorBefore);
                            /* the browser usually holds the anchor itself; line it up again if the
                               list above grew enough to slide it under the nav */
                            if (window.jumpUnderNav) { window.jumpUnderNav(hint); }
                        }
                    }).catch(function () {});
                }
            })
            .catch(function (problem) {
                if (problem && problem.message === 'login') { return; }
                form.submit();
            });
    });
}());
</script>

<fieldset id="refundedBlock">
    <legend>退款 / 关闭的单（登记后这些订单号不再入库）</legend>
    <form method="post" action="/mfg/control/refundedOrderSave" class="inline">
        <p>
            订单号 <input type="text" name="orderNo" size="22" placeholder="淘宝订单号"/>
            店铺 <input type="text" name="shop" size="16" placeholder="可空"/>
            备注 <input type="text" name="note" size="24" placeholder="可空，比如“截图写退款成功”"/>
            <input type="submit" value="登记"/>
        </p>
    </form>
    <p class="hint">识图认出「退款成功 / 已关闭」时，那张单只是这一次不入库，不会自动登记到这里；
        确实退了的就手工登记一行。
        钱退回来了、料一般也没留下——真入了库就是凭空多出一批货，所以这个号一登记就再也建不了单。</p>
    <#if refundedOrders?has_content>
        <table id="refundedTable">
            <tr><th>订单号</th><th>店铺</th><th>登记时间</th><th>备注</th><th>操作</th></tr>
            <#list refundedOrders as r>
                <tr data-refunded-order="${r.orderNo!}">
                    <td>${r.orderNo!}</td>
                    <td>${r.shop!}</td>
                    <td><#if r.recordedDate??>${r.recordedDate?string("yyyy-MM-dd HH:mm")}</#if></td>
                    <td>${r.note!}</td>
                    <td>
                        <form method="post" action="/mfg/control/refundedOrderDelete" class="inline">
                            <input type="hidden" name="orderNo" value="${r.orderNo!}"/>
                            <input type="submit" value="撤掉标记"/>
                        </form>
                    </td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有登记过退款单。</p>
    </#if>
</fieldset>

<fieldset>
    <legend>新建到货单</legend>
    <form method="post" action="/mfg/control/receiptSave">
        <p>
            供应商 <input type="text" name="supplierName" size="18" placeholder="淘宝店铺"/>
            店铺订单号 <input type="text" name="supplierOrderNo" size="20" placeholder="淘宝订单号（可空）"/>
            到货日 <input type="date" name="receiptDate" title="留空就是今天"/>
            运费 <input class="narrow" type="text" name="freightAmount" value="0" title="整单运费，记期间费用"/>
            优惠 <input class="narrow" type="text" name="discountAmount" value="0" title="整单优惠（红包 / 满减），可空"/>
            备注 <input type="text" name="note" size="20"/>
            <input type="submit" value="建单"/>
        </p>
    </form>
</fieldset>

<#if (parameters.voidedReceipt)?has_content>
    <p class="ok">已作废到货单 ${parameters.voidedReceipt}：它不再算进采购支出，批次重新变回可选；单据本身留着可查。
        批次与库存没有变动（要冲库存请到「库存 → 流水」按报废处理）。</p>
</#if>

<#if receipt.receiptId??>
    <#assign receiptVoid = ((receipt.statusId)!'MFG_RCPT_ACTIVE') == 'MFG_RCPT_VOID'>
    <h2 id="receiptDetail">到货单 ${receipt.receiptId}（${(rd.lineCount)!0} 行）<#if receiptVoid><span class="error">已作废</span></#if></h2>
    <#if receiptVoid>
        <p class="error">
            这张单已作废<#if receipt.voidedDate??>（${receipt.voidedDate?string("yyyy-MM-dd HH:mm")}<#if (receipt.voidedByUserLoginId)?has_content>，${receipt.voidedByUserLoginId}</#if><#if (receipt.voidReason)?has_content>，原因：${receipt.voidReason}</#if>）</#if>：
            不算采购支出，也不占批次；单据留着可查。
        </p>
        <p>
            <form method="post" action="/mfg/control/receiptRestore" class="inline" id="receiptRestoreForm">
                <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
                <input type="submit" class="secondary" value="恢复这张单"
                    onclick="return confirm('把到货单 ${receipt.receiptId} 恢复成有效？它会重新算进采购支出，并占住它的批次。')"/>
            </form>
            <span class="hint">作废作错了就恢复；恢复后又能编辑了。</span>
        </p>
    <#else>
        <p>
            <form method="post" action="/mfg/control/receiptVoid" class="inline" id="receiptVoidForm">
                <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
                原因 <input class="narrow" type="text" name="voidReason" value="建错了" size="14"
                    title="会记在单据上，例如：建错了 / 重复建单 / 供应商取消"/>
                <input type="submit" class="secondary" value="作废这张到货单"
                    onclick="return confirm('作废到货单 ${receipt.receiptId}？\n\n单据与 ${(rd.lineCount)!0} 行明细都留着可查，但不再算进采购支出，批次也不再占着（可以重新挂到别的单上）。\n批次、库存和流水完全不动。')"/>
            </form>
            <span class="hint">这张单建错了就作废：单据留痕、不算钱、批次放开；料和库存都不动。</span>
        </p>
    </#if>
    <#if receiptVoid>
        <p class="hint">作废的单不能再改：要改先点上面的「恢复这张单」。下面是它当时记下的内容。</p>
        <table id="receiptLinesVoid">
            <tr><th>供应商</th><th>店铺订单号</th><th>到货日</th><th class="num">运费</th><th class="num">优惠</th></tr>
            <tr>
                <td>${receipt.supplierName!}</td>
                <td>${receipt.supplierOrderNo!}</td>
                <td><#if receipt.receiptDate??>${receipt.receiptDate?string("yyyy-MM-dd")}</#if></td>
                <td class="num">${((receipt.freightAmount)!0)?string("0.00")}</td>
                <td class="num">${((receipt.discountAmount)!0)?string("0.00")}</td>
            </tr>
        </table>
        <#if lines?has_content>
            <table id="receiptLines">
                <tr><th class="num">行号</th><th>批次</th><th>料号</th><th>名称</th>
                    <th class="num">数量</th><th class="num">单价</th><th class="num">货款</th></tr>
                <#list lines as l>
                    <tr data-line-seq="${l.lineSeq}">
                        <td class="num">${(l.lineSeq)?string("0")}</td>
                        <td>${l.lotId!}</td>
                        <td>${l.componentCode!}</td>
                        <td>${l.componentName!}</td>
                        <td class="num">${((l.qty)!0)?string("0.##")}</td>
                        <td class="num">${((l.unitPriceExact)!0)?string("0.0000")}</td>
                        <td class="num">${((l.goodsAmount)!0)?string("0.00")}</td>
                    </tr>
                </#list>
            </table>
        <#else>
            <p class="hint">这张单当时没有挂批次。</p>
        </#if>
    <#else>
    <form method="post" action="/mfg/control/receiptSave" id="receiptSaveForm">
        <input type="hidden" name="receiptId" value="${receipt.receiptId}"/>
        <p>
            供应商 <input type="text" name="supplierName" size="18" value="${receipt.supplierName!''}"/>
            店铺订单号 <input type="text" name="supplierOrderNo" size="20" value="${receipt.supplierOrderNo!''}"/>
            到货日 <input type="date" name="receiptDate" value="<#if receipt.receiptDate??>${receipt.receiptDate?string("yyyy-MM-dd")}</#if>"/>
            运费 <input class="narrow" type="text" name="freightAmount" value="${((receipt.freightAmount)!0)?string("0.00")}"
                title="整单运费，记期间费用"/>
            优惠 <input class="narrow" type="text" name="discountAmount" value="${((receipt.discountAmount)!0)?string("0.00")}"
                title="整单优惠（红包 / 优惠券 / 满减）：实付 = 货款 + 运费 − 优惠"/>
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
    <script>
    /* opening a receipt from the list (or after voiding / saving) lands on the long list page first:
       bring the receipt itself into view. The offset keeps its heading below the sticky navigation
       bar; it runs after the page has loaded, because a browser first jumps to the #receiptDetail
       fragment itself and an old cached stylesheet carries no scroll-margin to correct that jump.
       The arithmetic lives in /mfg/js/jump.js, shared with the paste jumps (Step 115) */
    (function () {
        function jumpToReceipt() {
            var detail = document.getElementById('receiptDetail');
            if (detail && window.jumpUnderNav) { window.jumpUnderNav(detail); }
        }
        if (document.readyState === 'complete') {
            jumpToReceipt();
        } else {
            window.addEventListener('load', jumpToReceipt);
        }
    }());
    </script>
</#if>
