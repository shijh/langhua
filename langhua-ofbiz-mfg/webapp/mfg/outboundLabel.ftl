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
<#if labelSize != 'mid' && labelSize != 'small' && labelSize != 'strip' && labelSize != 'strip417'><#assign labelSize = 'big'></#if>
<#if labelSize == 'small'>
    <#assign pageW = '15mm' pageH = '20mm' qrMm = '12mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=small'
        qrText = (o.labelTextSmall)!''>
<#elseif labelSize == 'strip'>
    <#-- 长条（家用标签机常见的 15mm 胶带）：二维码在左，项目名在右，竖着排两行 -->
    <#assign pageW = '50mm' pageH = '15mm' qrMm = '12mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=small'
        qrText = (o.labelTextSmall)!''>
<#elseif labelSize == 'strip417'>
    <#-- 同一条 15mm 胶带，但码换成 PDF417：堆叠行天生适合长条，能多装好几倍的文字。
         码占 58×13mm（60mm 长的纸），项目名小字压在码下面。 -->
    <#assign pageW = '60mm' pageH = '15mm' qrMm = '58mm'
        qrSrc = '/mfg/control/outboundPdf417?outboundId=' + o.outboundId?url
        qrText = (o.labelTextSheet)!''>
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
        /* the same tape with a PDF417: a stacked code holds several times the text, so it takes
           58 x 13 mm of a 60 mm long label - two dots per module on a 203 dpi label printer */
        .label.strip417 { flex-direction: column; align-items: center; gap: .4mm; padding: .4mm; }
        .label.strip417 .qr { width: 58mm; height: 13mm; }
        .label.strip417 .qr img { width: 58mm; height: 13mm; }
        .label.strip417 .name {
            width: 58mm; font-size: 3pt; line-height: 1.1; text-align: center;
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
        <span class="hint">
            尺寸：
            <#if labelSize == 'big'><span class="current">大 32×42（码 32mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=big">大 32×42（码 32mm）</a></#if> ｜
            <#if labelSize == 'mid'><span class="current">中 20×26（码 16mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=mid">中 20×26（码 16mm）</a></#if> ｜
            <#if labelSize == 'small'><span class="current">小 15×20（码 12mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=small">小 15×20（码 12mm）</a></#if> ｜
            <#if labelSize == 'strip'><span class="current">长条 50×15（码 12mm）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=strip">长条 50×15（码 12mm）</a></#if> ｜
            <#if labelSize == 'strip417'><span class="current">长条 60×15（PDF417）</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=strip417">长条 60×15（PDF417）</a></#if>
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
</#if>
</body>
</html>
