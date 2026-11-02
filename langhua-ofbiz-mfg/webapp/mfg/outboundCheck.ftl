<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#assign o = (outboundResult.outbound)!{}>
<#assign key = (o.checkKey)!''>
<#assign given = (parameters.key)!''>
<#--
  扫码核对页：手机扫盒子上那张标签就到这一页，不用登录（地址里的 key 是单号的哈希，
  数单号猜不到别的箱子）。看的是这一盒该装哪些料、各多少件（Step 247）。
-->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>核对 ${(o.outboundId)!''}</title>
    <style>
        body { margin: 0; background: #f6f6f6; color: #111;
               font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif; }
        header { position: sticky; top: 0; background: #1f1f1f; color: #fff; padding: 12px 14px; }
        header .id { font-size: 22px; font-weight: 700; letter-spacing: .02em; }
        header .meta { font-size: 13px; color: #cfcfcf; margin-top: 4px; }
        .state { display: inline-block; margin-top: 6px; padding: 2px 10px; border-radius: 11px;
                 font-size: 13px; background: #3a3a3a; }
        .state.packed { background: #7a5c00; }
        .state.issued { background: #1e5c1e; }
        main { padding: 12px; }
        ol.lines { list-style: none; margin: 0; padding: 0; }
        ol.lines li { background: #fff; border: 1px solid #ddd; border-radius: 8px;
                      padding: 10px 12px; margin-bottom: 8px; }
        .code { font-size: 17px; font-weight: 700; word-break: break-all; }
        .name { font-size: 13px; color: #555; margin-top: 3px; word-break: break-all; }
        .num { float: right; font-size: 22px; font-weight: 700; margin-left: 10px; }
        .unit { font-size: 13px; font-weight: 400; color: #555; }
        .foot { font-size: 13px; color: #555; padding: 4px 2px 24px; line-height: 1.6; }
        .error { padding: 18px; font-size: 16px; }
    </style>
</head>
<body>
<#if !o?has_content>
    <p class="error">没有这张出库单：单号可能写错了。</p>
<#elseif key == '' || key != given>
    <p class="error">这个链接不完整（少了或写错了 key）：请重新扫一次盒子上的标签。</p>
<#else>
    <header>
        <div class="id">${o.outboundId}</div>
        <div class="meta">项目 ${(o.projectCode)!''}　BOM ${(o.bomVersion)!''}　${(o.lineCount)!0} 行　合计 ${(o.totalQty!0)?string("0.##")} 件</div>
        <span class="state<#if o.issued> issued<#elseif o.packed> packed</#if>"><#if o.issued>已出库<#elseif o.cancelled>已作废<#elseif o.packed>已包装，待出库<#else>待出库</#if></span>
    </header>
    <main>
        <p class="foot">
            包装 ${(o.packageCount!0)?string("0.##")} 盒<#if ((o.packingNote)!'')?has_content>（${o.packingNote}）</#if>
            <#if o.packedDate??>，${o.packedDate?string("MM-dd HH:mm")}</#if>
            <#if ((o.packedByUserLoginId)!'')?has_content>，${o.packedByUserLoginId} 包装</#if>。
        </p>
        <ol class="lines">
            <#list (o.lines)![] as ln>
                <li>
                    <span class="num">${(ln.qty!0)?string("0.##")}<span class="unit"> 件</span></span>
                    <div class="code">${ln.componentCode}</div>
                    <div class="name">${(ln.componentName)!}<#if ((ln.designators)!'')?has_content>　位号 ${ln.designators}</#if></div>
                </li>
            </#list>
        </ol>
        <p class="foot">
            核对：一件一件对着上面的料号和数量点，数量和单子一致就说明这一盒装对了；
            有出入先在盒子上记下来，回电脑上在出库单里改，别直接出库。
        </p>
    </main>
</#if>
</body>
</html>
