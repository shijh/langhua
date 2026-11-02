<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#assign o = (outboundResult.outbound)!{}>
<#--
  出库单标签，三种尺寸（Step 250）：
    big   80 × 50 mm  二维码 37mm，码里是整份清单（名称 + 数量）
    mid   50 × 30 mm  二维码 22mm，码里是短清单（截到 300 字节）
    small 15 × 30 mm  二维码 13mm，只写单号与「N 行 / M 件」——15mm 宽的胶带（精臣 D11 一类）
                      放不下十几行还能扫得清的字数
  这一页不走装饰器：它是打印的，不是浏览的。
-->
<#--
  注意：参数名不能叫 size —— FreeMarker 里 parameters['size'] / parameters.size 取到的是 Map 自己的
  size() 方法（比较时会报 “Left hand operand is a method+sequence”），所以页面参数叫 label。
-->
<#assign labelSize = (parameters['label'])!'big'>
<#if labelSize != 'mid' && labelSize != 'small'><#assign labelSize = 'big'></#if>
<#if labelSize == 'small'>
    <#assign pageW = '15mm' pageH = '30mm' qrMm = '13mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=small'
        qrText = (o.labelTextSmall)!''>
<#elseif labelSize == 'mid'>
    <#assign pageW = '50mm' pageH = '30mm' qrMm = '22mm'
        qrSrc = '/mfg/control/outboundQr?outboundId=' + o.outboundId?url + '&amp;size=mid'
        qrText = (o.labelTextMid)!''>
<#else>
    <#assign pageW = '80mm' pageH = '50mm' qrMm = '37mm'
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
            background: #fff; display: flex; gap: 1.5mm; padding: 0.5mm; margin: 6mm auto;
            border: 1px solid #bbb; overflow: hidden;
        }
        .qr { flex: 0 0 auto; width: ${qrMm}; height: ${qrMm}; }
        .qr img { width: ${qrMm}; height: ${qrMm}; image-rendering: pixelated; display: block; }
        .side { flex: 1 1 auto; min-width: 0; overflow: hidden; }
        .id { font-size: 13pt; font-weight: 700; letter-spacing: .02em; }
        .proj { font-size: 8pt; margin-top: .4mm; word-break: break-all; }
        .facts { margin-top: .8mm; font-size: 8pt; line-height: 1.3; }
        .facts div { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .big { font-weight: 700; }
        .small-note { font-size: 6.5pt; color: #333; margin-top: .8mm; }
        .label.mid .id { font-size: 10pt; }
        /* the small sticker stacks the code over two short lines: 15 mm of tape has no room beside it */
        .label.small { flex-direction: column; gap: .6mm; align-items: center; }
        .label.small .qr { margin: 0 auto; }
        .label.small .side { width: 100%; text-align: center; }
        .label.small .id { font-size: 5.5pt; letter-spacing: 0; word-break: break-all; line-height: 1.1; }
        .label.small .facts { font-size: 5pt; margin-top: .3mm; }
        .label.small .small-note, .label.small .proj { display: none; }
        .tools { text-align: center; margin: 4mm 0 8mm; font-size: 9pt; }
        .tools a, .tools button { font-size: 10pt; padding: 4px 10px; }
        .tools .current { font-weight: 700; color: #000; }
        .tools .hint { display: block; margin-top: 4px; color: #555; font-size: 8pt; }
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
    <div class="label ${labelSize}" data-label-size="${labelSize}">
        <div class="qr">
            <img src="${qrSrc}" alt="扫码看清单"/>
        </div>
        <div class="side">
            <div class="id">${o.outboundId}</div>
            <div class="proj">项目 ${(o.projectCode)!''}</div>
            <div class="facts">
                <div>BOM ${(o.bomVersion)!''}　${(o.lineCount)!0} 行　<span class="big">${(o.totalQty!0)?string("0.##")}</span> 件</div>
                <#if labelSize != 'small'>
                    <div>包装 <span class="big">${(o.packageCount!0)?string("0.##")}</span> 盒<#if ((o.packingNote)!'')?has_content>　${o.packingNote}</#if></div>
                    <div><#if o.issued>已出库<#elseif o.packed>已包装，待出库<#else>待出库</#if><#if o.packedDate??>　${o.packedDate?string("MM-dd HH:mm")}</#if></div>
                </#if>
                <#if labelSize == 'big'>
                    <div><#if ((o.packedByUserLoginId)!'')?has_content>包装 ${o.packedByUserLoginId}<#else>制作 ${(o.operatorName)!''}</#if></div>
                </#if>
            </div>
            <div class="small-note">手机扫码 → <#if labelSize == 'small'>单号与件数<#else>这一盒的料号和数量（清单就在码里）</#if></div>
        </div>
    </div>
    <#-- 码里的文字原样放在这里（不显示）：巡检用它断言“码里是名称和数量”/“小标签只有单号与件数” -->
    <pre id="qrText" data-qr-text="${qrText}" style="display:none">${qrText}</pre>
    <div class="tools">
        <button onclick="window.print()">打印这张标签</button>
        <a href="/mfg/control/outbound?outboundId=${o.outboundId?url}">回单据</a>
        <a href="/mfg/control/outboundCheck?outboundId=${o.outboundId?url}&amp;key=${o.checkKey!''}" target="_blank">核对页（浏览器打开）</a>
        <span class="hint">
            尺寸：
            <#if labelSize == 'big'><span class="current">大 80×50</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=big">大 80×50</a></#if> ｜
            <#if labelSize == 'mid'><span class="current">中 50×30</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=mid">中 50×30</a></#if> ｜
            <#if labelSize == 'small'><span class="current">小 15×30</span><#else><a href="?outboundId=${o.outboundId?url}&amp;label=small">小 15×30</a></#if>
            <br/>小标签是给 15mm 宽的蓝牙标签打印机（如精臣 D11）排的：二维码 13mm，只放单号和件数；
            胶带不一样就改这一页 `@page` 的尺寸（现在是 ${pageW} × ${pageH}）。
        </span>
    </div>
    <#-- 蓝牙标签机（精臣 D11 这类）大多只能用它自己的 App 打印，所以把码里的文字也显示出来：
         在 App 里新建同尺寸的标签 → 插入二维码 → 把这段粘进去，码里就是同样的内容 -->
    <div class="copybox">
        <p>码里的文字（在精臣 App 里：新建 ${pageW} × ${pageH} 标签 → 插入二维码 → 粘贴这段）：</p>
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
