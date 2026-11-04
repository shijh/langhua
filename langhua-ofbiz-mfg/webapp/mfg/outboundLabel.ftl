<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#assign o = (outboundResult.outbound)!{}>
<#--
  出库单标签（Step 251）：**三档都只印两样东西——二维码 + 项目名**。
  以前标签上还有单号、BOM 版本、行数、件数、盒数、状态，占掉大半张纸；去掉以后纸可以小很多。

    big    60 × 30 mm  二维码 28mm（码里是整份清单，上限 300 字节）
    mid    30 × 20 mm  二维码 18mm（码里是 单号 + 项目名 + N行/M件）
    small  15 × 20 mm  二维码 12mm（码里只有 单号 + N行/M件）—— 15mm 宽的标签打印机（蓝牙 / Wi-Fi 都一样）

  **不是随便缩的**：zxing 按字数挑版本，模块数 = 4×版本 + 17 + 两边各 4 个模块的静区（OFBiz 的
  generateQRCodeImage 用的是默认的纠错等级 L 和 4 模块边距）。所以一个字也不能少的 25 字节是
  v2 = 25+8 = 33 个模块，12mm 时模块 0.36mm；300 字节是 v11 = 61+8 = 69 个模块，28mm 时 0.41mm。
  模块低于 0.3mm 手机就很难对上了，这就是尺寸的下限，见 `check-outbound-pack.ps1` 的实测。
  单号、清单、核对页这些都还在单据页与「核对页」上，二维码里也带着（中/小档），不会丢。

  这一页不走装饰器：它是打印的，不是浏览的。
-->
<#--
  注意：参数名不能叫 size —— FreeMarker 里 parameters['size'] / parameters.size 取到的是 Map 自己的
  size() 方法（比较时会报 “Left hand operand is a method+sequence”），所以页面参数叫 label。
-->
<#assign labelSize = (parameters['label'])!'big'>
<#if labelSize != 'mid' && labelSize != 'small' && labelSize != 'strip'><#assign labelSize = 'big'></#if>
<#-- 蓝牙打印（D11，12mm 宽 / 96 点）永远是「小」码：12mm 只装得下 33 个模块的 v2 码 -->
<#assign d11QrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=small'>
<#if labelSize == 'small'>
    <#assign pageW = '15mm' pageH = '20mm' qrMm = '12mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=small'
        qrText = (o.labelTextSmall)!''>
<#elseif labelSize == 'strip'>
    <#-- 长条（家用标签机常见的 15mm 胶带）：二维码在左，项目名在右，竖着排两行 -->
    <#assign pageW = '50mm' pageH = '15mm' qrMm = '12mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=small'
        qrText = (o.labelTextSmall)!''>
<#elseif labelSize == 'mid'>
    <#assign pageW = '20mm' pageH = '26mm' qrMm = '16mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=mid'
        qrText = (o.labelTextMid)!''>
<#else>
    <#assign pageW = '32mm' pageH = '42mm' qrMm = '32mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url
        qrText = (o.labelText)!''>
</#if>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>出库单标签 ${(o.outboundId)!''}</title>
    <style>
        @page { size: ${pageW} ${pageH}; margin: 1mm; }
        html, body { margin: 0; padding: 0; }
        body { background: #f2f2f2; font-family: "Segoe UI", "Microsoft YaHei", sans-serif; color: #000; }
        .label {
            width: ${pageW}; height: ${pageH}; box-sizing: border-box;
            background: #fff; display: flex; flex-direction: column; justify-content: center;
            align-items: center; gap: .8mm; padding: 0.5mm;
            margin: 6mm auto; border: 1px solid #bbb; overflow: hidden;
        }
        .qr { flex: 0 0 auto; width: ${qrMm}; height: ${qrMm}; }
        .qr img { width: ${qrMm}; height: ${qrMm}; image-rendering: pixelated; display: block; }
        /* the name sits under the code, centred, small enough to take two or three lines */
        .name {
            width: 100%; max-width: 100%; text-align: center; font-weight: 700; line-height: 1.2;
            overflow-wrap: break-word; word-break: normal;
        }
        .label.big .name { font-size: 7pt; }
        .label.mid .name { font-size: 5pt; }
        /* 15 mm of tape leaves almost no width: a long word may break, the lines are shorter anyway */
        .label.small .name { font-size: 4.2pt; line-height: 1.15; overflow-wrap: anywhere; }
        /* the strip a home label printer makes: code on the left, the name beside it */
        .label.strip { flex-direction: row; align-items: center; gap: 1.2mm; padding: .6mm; }
        .label.strip .qr { width: 12mm; height: 12mm; }
        .label.strip .name {
            width: auto; flex: 1 1 auto; text-align: left; font-size: 5pt; line-height: 1.25;
            overflow-wrap: anywhere;
        }
        .tools { text-align: center; margin: 4mm 0 8mm; font-size: 9pt; }
        .tools a, .tools button { font-size: 10pt; padding: 4px 10px; }
        .tools .current { font-weight: 700; color: #000; }
        .tools .hint { display: block; margin-top: 6px; color: #555; font-size: 8pt; line-height: 1.5; }
        .copybox { max-width: 560px; margin: 0 auto 10mm; padding: 10px 12px; background: #fff; border: 1px solid #bbb; }
        .copybox p { margin: 0 0 6px; font-size: 9pt; }
        .copybox pre { margin: 0; padding: 8px; background: #f7f7f7; border: 1px dashed #aaa; white-space: pre-wrap; cursor: text; }
        .copybox .hint { color: #555; font-size: 8pt; margin: 6px 0 0; }
        /* 蓝牙打印（D11）：控件与日志都在 .tools 里，打印时跟着一起隐藏 */
        .d11 { display: block; margin-top: 10px; padding-top: 8px; border-top: 1px dashed #bbb; }
        .d11 label { margin-left: 8px; font-size: 9pt; }
        .d11 select, .d11 input { font-size: 9pt; }
        .d11 input { width: 3.5em; }
        .d11-state { display: block; margin-top: 6px; font-size: 9pt; color: #333; }
        .d11-state.error { color: #b00; }
        .d11-state.ok { color: #060; }
        .d11-log {
            max-width: 620px; margin: 8px auto 0; padding: 8px; max-height: 180px; overflow: auto;
            background: #fff; border: 1px solid #bbb; font-size: 8pt; line-height: 1.45;
            white-space: pre-wrap; text-align: left;
        }
        @media print {
            body { background: #fff; }
            .tools, .copybox { display: none; }
            .label { margin: 0; border: none; }
        }
    </style>
</head>
<body>
<#if !o?has_content>
    <p style="padding:12px">没有这张出库单：单号可能写错了。</p>
<#else>
    <div class="label ${labelSize}" data-label-size="${labelSize}" data-label-qr-mm="${qrMm}">
        <div class="qr">
            <img src="${qrSrc}" alt="扫码看这一盒"/>
        </div>
        <div class="name" title="${o.outboundId}">${(o.projectName)!''}</div>
    </div>
    <#-- 码里的文字原样放在这里（不显示）：巡检用它断言"码里是清单 / 单号与件数" -->
    <pre id="qrText" data-qr-text="${qrText}" style="display:none">${qrText}</pre>
    <div class="tools">
        <button onclick="window.print()">打印这张标签</button>
        <a href="/mfg/control/outbound?outboundId=${o.outboundId?url}">回单据</a>
        <a href="/mfg/control/outboundCheck?outboundId=${o.outboundId?url}&amp;key=${o.checkKey!''}" target="_blank">核对页（浏览器打开）</a>
        <span class="d11" data-d11="${o.outboundId}">
            <button id="d11Print" data-d11-print="1" data-d11-qr-mm="26">蓝牙打印</button>
            <label>纸型
                <select id="d11Paper" data-d11-paper="1">
                    <option value="40x30" selected>B1 40×30mm（203dpi / 384 点）</option>
                    <option value="50x30">B1 50×30mm（203dpi / 384 点）</option>
                    <option value="12x22">D11 12×22mm（203dpi / 96 点）</option>
                    <option value="12x30">D11 12×30mm（203dpi / 96 点）</option>
                    <option value="12x40">D11 12×40mm（203dpi / 96 点）</option>
                </select>
            </label>
            <label>流程
                <select id="d11Flow" data-d11-flow="1">
                    <option value="auto" selected>自动（按纸型）</option>
                    <option value="v1">老流程（D11 那种）</option>
                    <option value="v4">v4（开始 9 字节）</option>
                </select>
            </label>
            <label>码里
                <select id="d11Code" data-d11-code="1">
                    <option value="mid" selected>单号 + 项目名 + 件数</option>
                    <option value="big">整份清单</option>
                    <option value="small">只有单号</option>
                </select>
            </label>
            <label>份数 <input id="d11Copies" type="number" min="1" max="20" value="1"/></label>
            <label>浓度
                <select id="d11Density">
                    <option value="1">1 淡</option>
                    <option value="2" selected>2 中</option>
                    <option value="3">3 浓</option>
                </select>
            </label>
            <button id="d11Test" data-d11-test="1">打印测试条</button>
            <button id="d11Try" class="secondary" data-d11-try="1">试打四种组合</button>
            <button id="d11Probe" class="secondary" data-d11-probe="1">诊断（只问不打印）</button>
            <span id="d11State" class="d11-state" data-d11-state=""></span>
            <pre id="d11Log" class="d11-log" data-d11-log="1" hidden></pre>
            <span class="hint">
                用本机蓝牙直连标签机：点一下会弹出系统的蓝牙设备列表，选那台机器就行。
                前提是这个页面用 <b>Chrome / Edge</b> 打开、地址是 <code>http://localhost:端口</code>
                （自签证书的 https 被浏览器当成不安全，蓝牙会被拦）。
                纸型决定点数：<b>B1</b> 是 203dpi、打印头 384 点（48mm），40mm 的纸占中间 320 点；
                <b>D11</b> 是 203dpi、96 点宽（12mm 胶带）。位图按整条打印头画、内容居中，纸窄也能对上。
                纸上印二维码 + 单号 + 项目名 + 件数；「码里」选整份清单时只印码和项目名，码给得更大才扫得开。
                流程一般留「自动」；打出来是空白条时换成另一个流程再试，并把日志发我。
            </span>
        </span>
        <span class="hint">
            尺寸：
            <#if labelSize == 'big'><span class="current">大 32×42（码 32mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=big">大 32×42（码 32mm）</a></#if> ｜
            <#if labelSize == 'mid'><span class="current">中 20×26（码 16mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=mid">中 20×26（码 16mm）</a></#if> ｜
            <#if labelSize == 'small'><span class="current">小 15×20（码 12mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=small">小 15×20（码 12mm）</a></#if> ｜
            <#if labelSize == 'strip'><span class="current">长条 50×15（码 12mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=strip">长条 50×15（码 12mm）</a></#if>
            <br/>三种尺寸都只印二维码 + 项目名（项目名在码下面居中，小号字，可以换几行）。码越小能装的字越少：
            大标签里是整份清单（名称 + 数量），中标签是单号 + 项目名 + 件数，小标签只有单号 + 件数；
            模块小于 0.3mm 手机就对不上，所以 12mm 已经到底了。
            胶带比 15mm 更窄就改 `@page` 的尺寸（现在是 ${pageW} × ${pageH}，二维码 ${qrMm}）。
        </span>
    </div>
    <#-- 标签机（蓝牙或 Wi-Fi）大多只能用它自己的 App 打印，所以把码里的文字也显示出来：
         在 App 里新建同尺寸的标签 → 插入二维码 → 把这段粘进去，码里就是同样的内容 -->
    <div class="copybox">
        <p>码里的文字（在标签机 App 里：新建 ${pageW} × ${pageH} 标签 → 插入二维码 → 粘贴这段）：</p>
        <pre id="qrTextCopy" onclick="selectAll(this)">${qrText}</pre>
        <p class="hint">点一下上面的文字就全选，Ctrl+C 复制。</p>
    </div>
    <script>
        function selectAll(node) {
            var range = document.createRange();
            range.selectNodeContents(node);
            var selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
        }
    </script>
    <#-- 蓝牙打印（D11）：这一段只印二维码 + 单号 + 项目名 + 件数，纸 12mm 宽（96 点 / 203dpi）。
         文字放进元素里而不是属性里：服务返回的中文已经是数字实体（&#x884c;），再 ?html 一次
         会被当成字面量印到纸上。 -->
    <div id="d11Info" data-d11-info="1" data-qr="${d11QrSrc}" data-outbound="${o.outboundId}" hidden>
        <span id="d11Text">${(o.labelTextSmall)!''}</span>
        <span id="d11Project">${(o.projectName)!''}</span>
        <#-- 小码先在这里加载一次（隐藏），打印时直接用它，不再多跑一次请求 -->
        <img id="d11Qr" src="${d11QrSrc}" alt=""/>
    </div>
    <script src="/mfg/js/niimbot.js"></script>
    <script>
        (function () {
            'use strict';
            var info = document.getElementById('d11Info');
            var state = document.getElementById('d11State');
            var logBox = document.getElementById('d11Log');
            var printButton = document.getElementById('d11Print');
            var testButton = document.getElementById('d11Test');
            var tryButton = document.getElementById('d11Try');
            var probeButton = document.getElementById('d11Probe');
            var outboundId = (info && info.getAttribute('data-outbound')) || '';
            var driver = null;
            var busy = false;

            function say(text, kind) {
                if (!state) {
                    return;
                }
                state.textContent = text;
                state.className = 'd11-state' + (kind ? ' ' + kind : '');
                state.setAttribute('data-d11-state', kind || 'info');
            }

            function log(text) {
                if (!logBox) {
                    return;
                }
                logBox.hidden = false;
                logBox.textContent += text + '\n';
                logBox.scrollTop = logBox.scrollHeight;
            }

            function sleep(ms) {
                return new Promise(function (resolve) {
                    setTimeout(resolve, ms);
                });
            }

            function loadQr(url) {
                var ready = document.getElementById('d11Qr');
                /* 页面上预加载的是「小」码；要别的尺寸就现取一张，别再拿旧图去画 */
                if (ready && ready.getAttribute('src') === url && ready.complete && ready.naturalWidth > 0) {
                    return Promise.resolve(ready);
                }
                return new Promise(function (resolve, reject) {
                    var picture = new Image();
                    picture.onload = function () {
                        resolve(picture);
                    };
                    picture.onerror = function () {
                        reject(new Error('二维码图片取不回来：' + url));
                    };
                    picture.src = url;
                });
            }

            function fit(ctx, text, maxWidth) {
                if (ctx.measureText(text).width <= maxWidth) {
                    return text;
                }
                var cut = text;
                while (cut.length > 1 && ctx.measureText(cut + '…').width > maxWidth) {
                    cut = cut.slice(0, -1);
                }
                return cut + '…';
            }

            function font(size, weight) {
                return (weight ? weight + ' ' : '') + size + 'px "Microsoft YaHei", sans-serif';
            }

            /** 从 big 往小试，直到这一行放得下；都放不下就用 small（页面上再截断）。 */
            function shrinkTo(ctx, text, maxWidth, big, small, weight) {
                for (var size = big; size > small; size--) {
                    ctx.font = font(size, weight);
                    if (ctx.measureText(text).width <= maxWidth) {
                        return size;
                    }
                }
                ctx.font = font(small, weight);
                return small;
            }

            /** 按宽度折行（英文尽量在空格处断，中文直接按字断），最多 maxLines 行。 */
            function wrapLines(ctx, text, maxWidth, maxLines) {
                var rest = String(text || '').replace(/\s+/g, ' ').replace(/^ | $/g, '');
                var lines = [];
                while (rest && lines.length < maxLines) {
                    var take = rest.length;
                    while (take > 0 && ctx.measureText(rest.slice(0, take)).width > maxWidth) {
                        take--;
                    }
                    if (take === 0) {
                        break;
                    }
                    var cut = take;
                    if (take < rest.length) {
                        var space = rest.lastIndexOf(' ', take);
                        /* 阈值放到 35%：宁可第一行短一点，也别把一个英文词从中间拆开
                           （真机上 Cost test project 就被拆成了 “Cost test proje / ct”） */
                        if (space > take * 0.35) {
                            cut = space;
                        }
                    }
                    lines.push(rest.slice(0, cut));
                    rest = rest.slice(cut).replace(/^ /, '');
                }
                return lines;
            }

            /**
             * 把标签画成 0/1 位图。打印机只有黑白，所以这里直接按亮度阈值取黑（不做抖动——
             * 抖动会生成一片灰麻点，热敏头印出来更糊）。
             *
             * 点数跟着纸型走：D11 12mm 是 96 点宽（203dpi），B1 40mm 是 472 点宽（300dpi），
             * 所以字号与留白都按 `scale`（dpi / 203）放大，剩下的高度全给二维码——
             * 码的大小直接决定能不能扫开，宁肯字小一点。
             */
            function rasterise(picture, widthPx, heightPx, lines, scale, innerPx) {
                scale = scale || 1;
                /* 画布是整条打印头，内容只占纸宽（innerPx）那一段，居中 —— 纸比打印头窄时两边各留白 */
                var usable = Math.max(20, (innerPx || widthPx) - 4);
                var canvas = document.createElement('canvas');
                canvas.width = widthPx;
                canvas.height = heightPx;
                var ctx = canvas.getContext('2d');
                ctx.fillStyle = '#fff';
                ctx.fillRect(0, 0, widthPx, heightPx);
                ctx.fillStyle = '#000';
                ctx.textAlign = 'center';
                /* 先把文字排好，它占掉的高度才是二维码能用的高度 */
                var items = [];
                var bodySize = Math.round(13 * scale);
                if (lines.id) {
                    /* 单号是箱子上最要紧的字：先按 20px（×scale）试，放不下就一路缩 */
                    var numberSize = shrinkTo(ctx, lines.id, usable,
                        Math.round(20 * scale), Math.round(10 * scale), 'bold');
                    ctx.font = font(numberSize, 'bold');
                    items.push({
                        text: fit(ctx, lines.id, usable),
                        font: font(numberSize, 'bold'),
                        size: numberSize,
                        gap: Math.round(6 * scale)
                    });
                }
                if (lines.project) {
                    ctx.font = font(bodySize, '');
                    wrapLines(ctx, lines.project, usable, 2).forEach(function (one) {
                        items.push({ text: one, font: font(bodySize, ''), size: bodySize,
                            gap: Math.round(2 * scale) });
                    });
                }
                if (lines.counts) {
                    ctx.font = font(bodySize, '');
                    items.push({ text: fit(ctx, lines.counts, usable), font: font(bodySize, ''),
                        size: bodySize, gap: Math.round(6 * scale) });
                }
                var reserved = Math.round(10 * scale);
                items.forEach(function (item) {
                    reserved += item.size + item.gap;
                });
                var side = Math.max(Math.round(40 * scale),
                    Math.min(usable, heightPx - reserved));
                if (picture) {
                    ctx.drawImage(picture, Math.round((widthPx - side) / 2), 2, side, side);
                }
                var y = (picture ? 2 + side : 0) + Math.round(4 * scale);
                items.forEach(function (item) {
                    var baseline = y + item.size + item.gap;
                    if (baseline > heightPx - 1) {
                        return;
                    }
                    ctx.font = item.font;
                    ctx.fillText(item.text, widthPx / 2, baseline);
                    y = baseline;
                });
                var pixels = ctx.getImageData(0, 0, widthPx, heightPx).data;
                var bits = new Uint8Array(widthPx * heightPx);
                for (var i = 0; i < bits.length; i++) {
                    bits[i] = pixels[i * 4] < 128 ? 1 : 0;
                }
                return bits;
            }

            /** 把画好的位图往下挑 lead 行（顶上补白）：打印机开页会吞掉最上面十几行 */
            function padTop(bits, widthPx, areaH, heightPx, lead) {
                if (!lead) {
                    return bits;
                }
                var rows = Math.max(0, Math.min(areaH, heightPx - lead));
                var out = new Uint8Array(widthPx * heightPx);
                out.set(bits.subarray(0, widthPx * rows), widthPx * lead);
                return out;
            }

            /**
             * 测试条：上下各一条黑带 + 刻度 + 一行字。
             *
             * 上下两条黑带是为了看**上下边界**：内容压到标签最上沿时，上面那条会印到间隙或上一张纸上；
             * 下面那条能看出整页有没有印完。所以测试条要占满整张纸，不再只印 11mm。
             */
            function testBits(widthPx, heightPx, scale) {
                scale = scale || 1;
                var canvas = document.createElement('canvas');
                canvas.width = widthPx;
                canvas.height = heightPx;
                var ctx = canvas.getContext('2d');
                ctx.fillStyle = '#fff';
                ctx.fillRect(0, 0, widthPx, heightPx);
                ctx.fillStyle = '#000';
                var band = Math.round(10 * scale);
                ctx.fillRect(0, 0, widthPx, band);
                ctx.fillRect(0, heightPx - band, widthPx, band);
                for (var x = 0; x < widthPx; x += Math.round(8 * scale)) {
                    ctx.fillRect(x, band + Math.round(6 * scale), Math.round(4 * scale), Math.round(8 * scale));
                }
                ctx.textAlign = 'center';
                ctx.font = font(Math.round(20 * scale), 'bold');
                ctx.fillText('打印测试', widthPx / 2, Math.round(heightPx / 2) - Math.round(6 * scale));
                ctx.font = font(Math.round(13 * scale), '');
                ctx.fillText(widthPx + ' 点宽 / ' + heightPx + ' 行', widthPx / 2,
                    Math.round(heightPx / 2) + Math.round(14 * scale));
                var pixels = ctx.getImageData(0, 0, widthPx, heightPx).data;
                var bits = new Uint8Array(widthPx * heightPx);
                for (var i = 0; i < bits.length; i++) {
                    bits[i] = pixels[i * 4] < 128 ? 1 : 0;
                }
                return bits;
            }

            /* 纸型 → 点数与打印流程。实测（2026-11-04 诊断日志，B1-IA22170502）：
               `0x40[08]` 回 `48 1000` = 型号 0x1000（B1）、`0xDC[03]` 回 `…01 80…` = 打印头 384 点，
               所以 **B1 是 203dpi（8 点/mm）、打印头 48mm**，不是 300dpi（那是 B1 Pro）。
               纸 40mm 宽 = 320 点，位图按**整个打印头** 384 点宽画、内容居中，
               这样打印机把位图靠左还是居中都对得上纸。 */
            var PAPERS = {
                '40x30': { w: 40, h: 30, dpi: 203, head: 384, family: 'v1' },
                '50x30': { w: 50, h: 30, dpi: 203, head: 384, family: 'v1' },
                '12x22': { w: 12, h: 22, dpi: 203, head: 96, family: 'v1' },
                '12x30': { w: 12, h: 30, dpi: 203, head: 96, family: 'v1' },
                '12x40': { w: 12, h: 40, dpi: 203, head: 96, family: 'v1' }
            };

            function settings() {
                var key = (document.getElementById('d11Paper') || {}).value || '40x30';
                var paper = PAPERS[key] || PAPERS['40x30'];
                var flow = (document.getElementById('d11Flow') || {}).value || 'auto';
                var density = parseInt((document.getElementById('d11Density') || {}).value, 10) || 2;
                var copies = parseInt((document.getElementById('d11Copies') || {}).value, 10) || 1;
                return {
                    key: key,
                    /* 「流程」可以手动覆盖：自动 = 纸型说了算，v1 = D11 那种开始包，v4 = 9 字节开始包 */
                    family: flow === 'auto' ? paper.family : flow,
                    dpi: paper.dpi,
                    /* 位图按打印头的宽度画，内容限制在纸宽里居中 */
                    widthPx: paper.head,
                    innerPx: Math.min(paper.head, Math.round(paper.w * paper.dpi / 25.4)),
                    heightPx: Math.round(paper.h * paper.dpi / 25.4),
                    /* 打印机开页会把最上面十几行吞掉（真机实测），所以内容整体下移这么多行、顶上补白 */
                    leadRows: paper.head > 96 ? 12 : 0,
                    scale: paper.dpi / 203,
                    mm: paper.h,
                    code: (document.getElementById('d11Code') || {}).value || 'mid',
                    density: Math.max(1, Math.min(3, density)),
                    copies: Math.max(1, Math.min(20, copies))
                };
            }

            function qrUrl(code) {
                return '/mfg/control/outboundQr?outboundId=' + encodeURIComponent(outboundId)
                    + '&size=' + code;
            }

            function connected() {
                if (!driver) {
                    driver = new Niimbot.Driver(log);
                }
                return driver.characteristic ? Promise.resolve(driver) : driver.connect();
            }

            function wire(button, label, build) {
                if (!button) {
                    return;
                }
                button.addEventListener('click', function () {
                    if (busy) {
                        return;
                    }
                    busy = true;
                    button.disabled = true;
                    var job = settings();
                    say(label + '：准备中…');
                    Promise.resolve().then(function () {
                        return build(job);
                    }).then(function (built) {
                        var bits = built && built.bits ? built.bits : built;
                        var heightPx = built && built.heightPx ? built.heightPx : job.heightPx;
                        say(label + '：正在连接蓝牙…');
                        return connected().then(function () {
                            say(label + '：已连接 ' + driver.deviceName() + '，正在打印…');
                            return driver.print(bits, job.widthPx, heightPx, {
                                density: job.density,
                                copies: job.copies,
                                family: job.family
                            });
                        });
                    }).then(function (result) {
                        if (result && result.printed === false) {
                            say(label + '：位图已全部发出，但打印机没有回报进度——看纸出没出来；'
                                + '没出来的话点「诊断」把日志发我', 'error');
                        } else {
                            say(label + '：已发送 ' + job.copies + ' 份（' + job.key + '）', 'ok');
                        }
                    }).catch(function (error) {
                        say(label + '失败：' + (error && error.message ? error.message : error), 'error');
                        log('× ' + (error && error.message ? error.message : error));
                    }).then(function () {
                        busy = false;
                        button.disabled = false;
                    });
                });
            }

            if (!window.Niimbot || !Niimbot.supported()) {
                var why = window.Niimbot ? Niimbot.whyNotSupported() : '蓝牙驱动没加载（/mfg/js/niimbot.js）';
                say(why, 'error');
                if (printButton) {
                    printButton.disabled = true;
                }
                if (testButton) {
                    testButton.disabled = true;
                }
                if (probeButton) {
                    probeButton.disabled = true;
                }
                return;
            }

            var text = (document.getElementById('d11Text').textContent || '').split('\n');
            var projectName = (document.getElementById('d11Project').textContent || '').trim();
            wire(printButton, '蓝牙打印', function (job) {
                return loadQr(qrUrl(job.code)).then(function (picture) {
                    /* 整份清单那张码密得多，占到整张纸才扫得开，所以那种情况下纸上只留项目名 */
                    var compact = job.code === 'big';
                    var areaH = Math.max(20, job.heightPx - job.leadRows);
                    var bits = rasterise(picture, job.widthPx, areaH, {
                        id: compact ? '' : (text[0] || '').trim(),
                        project: projectName,
                        counts: compact ? '' : (text[1] || '').trim()
                    }, job.scale, job.innerPx);
                    return padTop(bits, job.widthPx, areaH, job.heightPx, job.leadRows);
                });
            });
            wire(testButton, '测试条', function (job) {
                /* 测试条印满整张标签：上下两条黑带就是上下边界，能看出有没有压到边上 */
                var height = job.heightPx;
                var areaH = Math.max(20, height - job.leadRows);
                return {
                    bits: padTop(testBits(job.widthPx, areaH, job.scale),
                        job.widthPx, areaH, height, job.leadRows),
                    heightPx: height
                };
            });

            /* 试打四种组合：真机上「所有命令都应答、纸上却什么都没有」时，问题在流程或行包格式。
               一次打四张测试条，日志里写明每张是哪种组合，人看哪张印出来就知道该用哪套。 */
            if (tryButton) {
                tryButton.addEventListener('click', function () {
                    if (busy) {
                        return;
                    }
                    busy = true;
                    tryButton.disabled = true;
                    var job = settings();
                    var variants = [
                        { family: 'v1', countBytes: 4, name: '老流程 + 黑点数 4 字节' },
                        { family: 'v4', countBytes: 4, name: 'v4（开始 9 字节）+ 黑点数 4 字节' },
                        { family: 'v1', countBytes: 1, name: '老流程 + 黑点数 1 字节' },
                        { family: 'v4', countBytes: 1, name: 'v4（开始 9 字节）+ 黑点数 1 字节' }
                    ];
                    var height = job.heightPx;
                    var areaH = Math.max(20, height - job.leadRows);
                    var bits = padTop(testBits(job.widthPx, areaH, job.scale),
                        job.widthPx, areaH, height, job.leadRows);
                    say('试打：正在连接蓝牙…');
                    connected().then(function () {
                        var chain = Promise.resolve();
                        variants.forEach(function (variant, index) {
                            chain = chain.then(function () {
                                say('试打第 ' + (index + 1) + ' / ' + variants.length + ' 张：' + variant.name);
                                log('—— 第 ' + (index + 1) + ' 张：' + variant.name + ' ——');
                                return driver.print(bits, job.widthPx, height, {
                                    density: job.density,
                                    copies: 1,
                                    family: variant.family,
                                    countBytes: variant.countBytes
                                });
                            }).then(function () {
                                return sleep(2500);
                            });
                        });
                        return chain;
                    }).then(function () {
                        say('试打结束：' + variants.length + ' 张里哪一张印出来了？把日志发我', 'ok');
                    }).catch(function (error) {
                        say('试打失败：' + (error && error.message ? error.message : error), 'error');
                        log('× ' + (error && error.message ? error.message : error));
                    }).then(function () {
                        busy = false;
                        tryButton.disabled = false;
                    });
                });
            }

            /* 诊断：不打印，只把“打印机到底回什么”写进日志 */
            if (probeButton) {
                probeButton.addEventListener('click', function () {
                    if (busy) {
                        return;
                    }
                    busy = true;
                    probeButton.disabled = true;
                    say('诊断：正在连接蓝牙…');
                    connected().then(function () {
                        say('诊断：已连接 ' + driver.deviceName() + '，正在逐条问…');
                        return driver.diagnose();
                    }).then(function (heard) {
                        say('诊断结束：打印机答了 ' + heard.length + ' 条（日志里 ← 开头的是它的原话）',
                            heard.length ? 'ok' : 'error');
                    }).catch(function (error) {
                        say('诊断失败：' + (error && error.message ? error.message : error), 'error');
                        log('× ' + (error && error.message ? error.message : error));
                    }).then(function () {
                        busy = false;
                        probeButton.disabled = false;
                    });
                });
            }
        })();
    </script>
</#if>
</body>
</html>
