<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  One part number: its picture, its numbers, its files and the batches that carry it.

  The files hang on the part number (MFG_ATT_PART), not on a batch, so a datasheet uploaded once
  shows for every batch bought later. They are stored in MfgAttachment like every other attachment
  of the component, so the upload form, the delete button and the inline viewer are the same ones
  the product and claim pages use.
-->
<#assign docs = (docResult.attachments)![]>
<h1>元件资料</h1>
<#if !((part.componentCode)!'')?has_content>
    <p class="hint">先选一个元件：在「物料批次」或「库存结存」里点料号后面的「资料」，就会到这里来。</p>
<#else>
    <#assign code = part.componentCode>
    <h2><code>${code}</code> <#if (part.componentName)!''?has_content><span class="hint">${part.componentName}</span></#if></h2>

    <table>
        <tr><th>料号</th><th>名称</th><th class="num">在库</th><th class="num">已锁</th><th class="num">可发</th><th class="num">批次</th><th>商品图</th></tr>
        <tr>
            <td><code>${code}</code></td>
            <td>${(part.componentName)!'-'}</td>
            <td class="num">${((part.onHandQty)!0)?string("0.##")}</td>
            <td class="num">${((part.reservedQty)!0)?string("0.##")}</td>
            <td class="num">${((part.availableQty)!0)?string("0.##")}</td>
            <td class="num">${(part.lotCount)!0}</td>
            <td><#if (part.scanShotId!'')?has_content><a
                    href="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${part.scanShotId?url}"
                    target="_blank"><img class="shot" data-part-shot="${part.scanShotId}" alt=""
                    src="/mfg/control/attachment?ownerTypeId=MFG_ATT_ORDER_SCAN&amp;ownerId=${part.scanShotId?url}&amp;thumb=1"/></a><#else><span class="hint">-</span></#if></td>
        </tr>
    </table>

    <#include "attachmentBlock.ftl">
    <#-- the owner of the files is the part number, so the page can be reached from any batch of it -->
    <#assign attOwner = code>
    <#assign attBack = '/mfg/control/partDocs?componentCode=' + (code?url)>
    <h2>资料（${docs?size} 个）</h2>
    <@attachmentBlock 'MFG_ATT_PART' attOwner attBack '规格书 / 图纸 / 数据手册（PDF 可以直接在浏览器里打开）' docs/>

    <h2>这个料号的批次（${(part.lots)?size} 个）</h2>
    <#if (part.lots)?has_content>
        <table>
            <tr><th>批次</th><th>名称</th><th>供应商</th><th class="num">到货</th><th class="num">在库</th>
                <th>到货日期</th><th>来料检验</th><th>说明</th></tr>
            <#list part.lots as lot>
                <tr data-lot-row="${lot.lotId}">
                    <td>${lot.lotId!}<#if (lot.splitFromLotId!'')?has_content><br/><span class="hint">拆自 ${lot.splitFromLotId}</span></#if></td>
                    <td>${(lot.componentName)!''}</td>
                    <td>${(lot.supplierName)!''}</td>
                    <td class="num">${((lot.receivedQty)!0)?string("0.##")}</td>
                    <td class="num">${((lot.remainingQty)!0)?string("0.##")}</td>
                    <td><#if lot.receivedDate??>${lot.receivedDate?string("yyyy-MM-dd")}</#if></td>
                    <td><#if (lot.inspectStatusId!'') == 'MFG_IQC_PASS'><span class="ok">合格</span>
                        <#elseif (lot.inspectStatusId!'') == 'MFG_IQC_CONCESSION'><span class="warn">让步接收</span>
                        <#elseif (lot.inspectStatusId!'') == 'MFG_IQC_FAIL'><span class="error">不合格</span>
                        <#else><span class="hint">待检</span></#if></td>
                    <td><a href="/mfg/control/materials?search=${lot.lotId?url}">在台账里看</a>
                        <a href="/mfg/control/stockMoves?lotId=${lot.lotId?url}">流水</a></td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">这个料号还没有批次。资料可以先传上来，等货到了直接就能用。</p>
    </#if>
</#if>
