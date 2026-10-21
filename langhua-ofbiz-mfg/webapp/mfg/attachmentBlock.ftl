<#ftl encoding="UTF-8">
<#--
  Reusable attachment block: upload form, list with optional thumbnails and a delete button.

  Usage: <@attachmentBlock ownerTypeId ownerId back title attachments/>
    ownerTypeId  MFG_ATT_PRODUCT / DEV_PROJECT / WORK_ORDER / DELIVERY / CLAIM
    ownerId      key of the record
    back         path the upload and the delete go back to, e.g. /mfg/control/claims?claimId=RMA-261021-01
    title         legend of the block
    attachments  list returned by mfgAttachmentList
-->
<#macro attachmentBlock ownerTypeId ownerId back title attachments>
    <#if (parameters.err!'') != ''>
        <p class="error">
            <#if parameters.err == 'size'>文件太大：图片 ≤ 4 MB，PDF ≤ 8 MB
            <#elseif parameters.err == 'type'>只支持 JPG / PNG / GIF / PDF
            <#elseif parameters.err == 'nofile'>请先选择文件
            <#elseif parameters.err == 'owner'>这条记录还不存在，先保存记录再上传
            <#else>上传失败，请检查文件后重试</#if>
        </p>
    </#if>
    <fieldset>
        <legend>${title}</legend>
        <form method="post" action="/mfg/control/attachmentUpload" enctype="multipart/form-data">
            <input type="hidden" name="ownerTypeId" value="${ownerTypeId}"/>
            <input type="hidden" name="ownerId" value="${ownerId}"/>
            <input type="hidden" name="back" value="${back}"/>
            <p>
                <input type="file" name="file" accept="image/jpeg,image/png,image/gif,application/pdf"/>
                <input type="text" name="note" size="22" placeholder="备注：正面 / 破损 / 合同扫描件"/>
                <input type="submit" value="上传"/>
            </p>
            <p class="hint">支持 JPG / PNG / GIF / PDF；图片 ≤ 4 MB，PDF ≤ 8 MB</p>
        </form>
    </fieldset>
    <#if attachments?has_content>
        <table>
            <tr><th>文件</th><th class="num">序号</th><th>文件名</th><th class="num">大小</th><th>备注</th><th>上传时间</th><th>操作</th></tr>
            <#list attachments as att>
                <tr>
                    <td>
                        <#if att.isImage>
                            <a href="/mfg/control/attachment?ownerTypeId=${ownerTypeId}&ownerId=${ownerId}&seq=${att.seq}" target="_blank">
                                <img src="/mfg/control/attachment?ownerTypeId=${ownerTypeId}&ownerId=${ownerId}&seq=${att.seq}&thumb=1" alt="${att.fileName!}" height="80" style="border: 1px solid #ddd;"/>
                            </a>
                        <#else>
                            <a href="/mfg/control/attachment?ownerTypeId=${ownerTypeId}&ownerId=${ownerId}&seq=${att.seq}" target="_blank">查看 PDF</a>
                        </#if>
                    </td>
                    <td class="num">${att.seq}</td>
                    <td>${att.fileName!}</td>
                    <td class="num">${((att.fileSize!0) / 1024)?string("0")} KB</td>
                    <td>${att.note!}</td>
                    <td><#if att.createdStamp??>${att.createdStamp?string("yyyy-MM-dd HH:mm")}</#if></td>
                    <td>
                        <form method="post" action="/mfg/control/attachmentDelete" class="inline">
                            <input type="hidden" name="ownerTypeId" value="${ownerTypeId}"/>
                            <input type="hidden" name="ownerId" value="${ownerId}"/>
                            <input type="hidden" name="seq" value="${att.seq}"/>
                            <input type="hidden" name="back" value="${back}"/>
                            <input type="submit" value="删除"/>
                        </form>
                    </td>
                </tr>
            </#list>
        </table>
    <#else>
        <p class="hint">还没有附件。</p>
    </#if>
</#macro>
