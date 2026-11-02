<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#assign o = (outboundResult.outbound)!{}>
<#--
  出库单标签：80mm × 50mm，贴到装料的塑料盒上。左边二维码，手机上扫一下打开核对页
  （/mfg/control/outboundCheck，不用登录，地址里带 key）；右边是盒子上要看的那几行。
  这一页不走装饰器：它是打印的，不是浏览的（Step 247）。
-->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>出库单标签 ${(o.outboundId)!''}</title>
    <style>
        @page { size: 80mm 50mm; margin: 2mm; }
        html, body { margin: 0; padding: 0; }
        body { background: #f2f2f2; font-family: "Segoe UI", "Microsoft YaHei", sans-serif; color: #000; }
        .label {
            width: 80mm; height: 50mm; box-sizing: border-box; padding: 2mm;
            background: #fff; display: flex; gap: 2.5mm; margin: 6mm auto; border: 1px solid #bbb;
        }
        .qr { flex: 0 0 auto; width: 33mm; height: 33mm; }
        .qr img { width: 33mm; height: 33mm; image-rendering: pixelated; display: block; }
        .side { flex: 1 1 auto; min-width: 0; font-size: 8pt; line-height: 1.3; }
        .id { font-size: 13pt; font-weight: 700; letter-spacing: .02em; }
        .proj { font-size: 8.5pt; margin-top: .6mm; word-break: break-all; }
        .facts { margin-top: 1mm; }
        .facts div { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .big { font-size: 10pt; font-weight: 700; }
        .small { font-size: 7pt; color: #333; margin-top: 1mm; }
        .tools { text-align: center; margin: 4mm 0 8mm; }
        .tools a, .tools button { font-size: 10pt; padding: 4px 10px; }
        @media print {
            body { background: #fff; }
            .tools { display: none; }
            .label { margin: 0; border: none; }
        }
    </style>
</head>
<body>
<#if !o?has_content>
    <p style="padding:12px">没有这张出库单：单号可能写错了。</p>
<#else>
    <div class="label">
        <div class="qr">
            <img src="/mfg/control/outboundQr?outboundId=${o.outboundId?url}" alt="扫码核对"/>
        </div>
        <div class="side">
            <div class="id">${o.outboundId}</div>
            <div class="proj">项目 ${(o.projectCode)!''}</div>
            <div class="facts">
                <div>BOM ${(o.bomVersion)!''}　${(o.lineCount)!0} 行　合计 <span class="big">${(o.totalQty!0)?string("0.##")}</span> 件</div>
                <div>包装 <span class="big">${(o.packageCount!0)?string("0.##")}</span> 盒<#if ((o.packingNote)!'')?has_content>　${o.packingNote}</#if></div>
                <div><#if o.issued>已出库<#elseif o.packed>已包装，待出库<#else>待出库</#if><#if o.packedDate??>　${o.packedDate?string("MM-dd HH:mm")}</#if></div>
                <div><#if ((o.packedByUserLoginId)!'')?has_content>包装 ${o.packedByUserLoginId}<#else>制作 ${(o.operatorName)!''}</#if></div>
            </div>
            <div class="small">手机扫码 → 核对这张单的料号和数量</div>
        </div>
    </div>
    <div class="tools">
        <button onclick="window.print()">打印这张标签</button>
        <a href="/mfg/control/outbound?outboundId=${o.outboundId?url}">回单据</a>
        <a href="/mfg/control/outboundCheck?outboundId=${o.outboundId?url}&amp;key=${o.checkKey!''}" target="_blank">先在电脑上看看核对页</a>
    </div>
</#if>
</body>
</html>
