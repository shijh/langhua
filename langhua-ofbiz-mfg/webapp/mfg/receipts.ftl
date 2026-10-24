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
            var refundField = document.getElementById('refundField');
            /* a refunded or closed order never arrived: keep it out of stock unless the person
               says the goods did arrive after all */
            if (refundField && refundField.value === 'Y') {
                event.preventDefault();
                var refundBlock = document.getElementById('refundBlock');
                if (refundBlock) {
                    refundBlock.hidden = false;
                    refundBlock.className = 'error';
                }
                var refundAccept = document.getElementById('refundAccept');
                if (refundAccept) { refundAccept.focus(); }
                return;
            }
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
    <p>
        截图识别
        <input type="file" id="orderImage" name="image" accept="image/png,image/jpeg"/>
        <button type="button" id="scanOrder" data-once="Y">识别并填入</button>
        <span id="scanHint" class="hint">选文件后点按钮，或者直接 <kbd>Ctrl+V</kbd> 粘贴截图（也可以把图拖进来）；
            没配识图服务就把明细粘到下面。</span>
    </p>
    <form method="post" action="/mfg/control/receiptFromOrder" id="orderForm">
        <input type="hidden" name="overwrite" id="overwriteField" value="N"/>
        <input type="hidden" name="refunded" id="refundField" value="N"/>
        <p id="refundBlock" hidden="hidden">
            这张单显示<strong>已退款 / 已关闭</strong>（截图上的「退款成功」「退款中」「交易关闭」），
            按规矩<strong>不入库</strong>——钱退回来了，料一般也没留下。这个订单号已经记到下面的
            「退款 / 关闭的单」里，以后再提交也进不来；真是付了两次或换货，到那里把这条撤掉再提交。
        </p>
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
    <div id="scanOrders" hidden="hidden">
        <p id="batchHint" class="hint"></p>
        <table class="inner" id="scanOrderTable">
            <tr>
                <th>订单号</th><th>店铺</th><th>下单</th><th class="num">运费</th>
                <th class="num">明细</th><th class="num">货款</th><th>状态</th>
            </tr>
            <tbody id="scanOrderRows"></tbody>
        </table>
        <p>
            <button type="button" id="importOrders" data-once="Y">导入这些单</button>
            <span class="hint">一张截图里有几张单就建几张到货单；已退款的单不入库，
                已经入过库的明细会跳过（不会重复）。</span>
        </p>
    </div>
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
        /* a refunded or closed order stays out of the stock: the banner under the form says so and
           the order number has been put on the list below by the recognition service */
        var refundField = document.getElementById('refundField');
        var refundBlock = document.getElementById('refundBlock');
        function showRefund(isRefunded) {
            if (!refundField || !refundBlock) {
                return;
            }
            refundField.value = isRefunded ? 'Y' : 'N';
            refundBlock.hidden = !isRefunded;
            refundBlock.className = isRefunded ? 'error' : '';
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
            /* recognition takes a few seconds, so the button stays dimmed (and unclickable) until
               the answer is in: once.js only covers the double click itself */
            var release = function () { button.className = button.className.replace(/\s*\bbusy\b/g, ''); };
            button.className = (button.className + ' busy').replace(/^\s+/, '');
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
                    showRefund(data.refunded === 'Y');
                    if (data.orders && data.orders.length > 1) {
                        showOrders(data.orders);
                        hint.className = 'ok';
                        hint.textContent = data.message || ('识别到 ' + data.orders.length + ' 张单');
                        return;
                    }
                    hideOrders();
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
                })
                .then(release, release);
        });

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
        function showOrders(found) {
            orders = found;
            if (!ordersBox || !orderRows) { return; }
            while (orderRows.firstChild) { orderRows.removeChild(orderRows.firstChild); }
            found.forEach(function (order) {
                var row = document.createElement('tr');
                row.setAttribute('data-scan-order', order.orderNo || '');
                cell(row, order.orderNo || '（没读到单号）');
                cell(row, order.shop || '');
                cell(row, order.orderDate || '');
                cell(row, order.freight || '0', 'num');
                cell(row, linesOf(order).length + ' 行', 'num');
                cell(row, amountsOf(order).toFixed(2), 'num');
                var status = cell(row, order.refunded === 'Y' ? '已退款，不入库' : '待导入');
                if (order.refunded === 'Y') { status.className = 'error'; }
                orderRows.appendChild(row);
            });
            ordersBox.hidden = false;
            if (batchHint) {
                var notes = found.filter(function (order) {
                    return order.note;
                }).map(function (order) {
                    return (order.orderNo || '一张单') + '：' + order.note;
                });
                batchHint.className = 'hint';
                batchHint.textContent = '这张截图里共 ' + found.length + ' 张单，核对后点「导入这些单」。'
                    + (notes.length ? ' ' + notes.join('；') : '');
            }
        }
        function hideOrders() {
            orders = [];
            if (ordersBox) { ordersBox.hidden = true; }
        }
        if (importButton) {
            importButton.addEventListener('click', function () {
                if (!orders.length) { return; }
                var params = new URLSearchParams();
                var wanted = [];
                orders.forEach(function (order) {
                    if (order.refunded === 'Y') { return; }
                    var index = wanted.length;
                    wanted.push(order);
                    params.append('o' + index + '.orderNo', order.orderNo || '');
                    params.append('o' + index + '.shop', order.shop || '');
                    params.append('o' + index + '.freight', order.freight || '0');
                    params.append('o' + index + '.lines', order.lines || '');
                });
                var skipped = orders.length - wanted.length;
                if (!wanted.length) {
                    if (batchHint) {
                        batchHint.className = 'error';
                        batchHint.textContent = '这几张单都是退款/关闭的，按规矩不入库。';
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
                    .then(function (answer) { return answer.json(); })
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
                                + (skipped > 0 ? '，' + skipped + ' 张退款单按规矩没入' : '')
                                + ((done + already) < wanted.length ? '，其余见每行的原因' : '') + '。';
                        }
                        importButton.className = importButton.className.replace(/\s*\bbusy\b/g, '');
                        // the new receipts must show up in the list above
                        fetch(location.href, { credentials: 'same-origin' })
                            .then(function (answer) { return answer.text(); })
                            .then(function (html) {
                                var fresh = new DOMParser().parseFromString(html, 'text/html')
                                    .getElementById('receiptListBlock');
                                var current = document.getElementById('receiptListBlock');
                                if (fresh && current) { current.innerHTML = fresh.innerHTML; }
                            });
                    })
                    .catch(function () {
                        importButton.className = importButton.className.replace(/\s*\bbusy\b/g, '');
                        if (batchHint) {
                            batchHint.className = 'error';
                            batchHint.textContent = '导入请求失败了，刷新页面看看已经进去了几张。';
                        }
                    });
            });
        }
        function fill(id, value) {
            if (!value) { return; }
            var field = document.getElementById(id);
            if (field) { field.value = value; }
        }
    }());
    </script>
</fieldset>

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
    <p class="hint">截图里能看出「退款成功 / 已关闭」时系统会自动登记（列表里会多一行）；
        手工粘单、或者截图没写字但你知道退了款的，可以在这里补登。
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
