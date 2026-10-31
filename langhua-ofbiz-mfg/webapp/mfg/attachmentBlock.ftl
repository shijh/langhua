<#ftl encoding="UTF-8">
<#--
  Reusable attachment block: upload form, list with optional thumbnails and a delete button.

  Usage: <@attachmentBlock ownerTypeId ownerId back title attachments/>
    ownerTypeId  MFG_ATT_PRODUCT / DEV_PROJECT / WORK_ORDER / DELIVERY / CLAIM / PART (a part number)
    ownerId      key of the record
    back         path the upload and the delete go back to, e.g. /mfg/control/claims?claimId=RMA-261021-01
    title         legend of the block
    attachments  list returned by mfgAttachmentList

  A file can also be pasted (Ctrl+V) and uploaded with a bare Enter: the block fills its own file
  input through a DataTransfer and hands the key to js/enter.js, the same way the order screenshot
  and the workbook blocks do (Step 186/202). One page only ever has one attachment block, so the ids
  and the Enter name are derived from the owner type.
-->
<#macro attachmentBlock ownerTypeId ownerId back title attachments>
    <#assign suffix = ownerTypeId?replace('MFG_ATT_', '')>
    <#assign formId = 'attForm' + suffix>
    <#assign fileId = 'attFile' + suffix>
    <#assign buttonId = 'attUpload' + suffix>
    <#assign hintId = 'attHint' + suffix>
    <#if (parameters.err!'') != ''>
        <p class="error">
            <#if parameters.err == 'size'>文件太大：图片 ≤ 4 MB，PDF ≤ 8 MB
            <#elseif parameters.err == 'type'>只支持 JPG / PNG / GIF / PDF
            <#elseif parameters.err == 'nofile'>请先选择文件
            <#elseif parameters.err == 'owner'>这条记录还不存在，先保存记录再上传
            <#else>上传失败，请检查文件后重试</#if>
        </p>
    </#if>
    <fieldset id="attachments">
        <legend>${title}</legend>
        <form method="post" action="/mfg/control/attachmentUpload" enctype="multipart/form-data" id="${formId}">
            <input type="hidden" name="ownerTypeId" value="${ownerTypeId}"/>
            <input type="hidden" name="ownerId" value="${ownerId}"/>
            <#-- come back to this block, not to the top of the page (UI rule: 改完停在原地) -->
            <input type="hidden" name="back" value="${back}#attachments"/>
            <p>
                <input type="file" name="file" id="${fileId}" accept="image/jpeg,image/png,image/gif,application/pdf"/>
                <input type="text" name="note" size="22" placeholder="备注：正面 / 破损 / 合同扫描件"/>
                <input type="submit" value="上传" id="${buttonId}"/>
            </p>
            <p class="hint" id="${hintId}">支持 JPG / PNG / GIF / PDF；图片 ≤ 4 MB，PDF ≤ 8 MB。
                也可以把文件复制过来，在这里按 <b>Ctrl+V</b> 粘进来，然后直接按 <b>回车</b>上传。</p>
        </form>
        <script>
            (function () {
                var form = document.getElementById('${formId}');
                var fileField = document.getElementById('${fileId}');
                var button = document.getElementById('${buttonId}');
                var hint = document.getElementById('${hintId}');
                if (!form || !fileField || !button || !hint) { return; }
                var name = 'attachments';
                function say(text, style) {
                    hint.className = style || 'hint';
                    hint.textContent = text;
                }
                /* what this block takes: exactly the four types the upload endpoint stores */
                function attachmentish(fileLike) {
                    var fileName = (fileLike && fileLike.name ? fileLike.name : '').toLowerCase();
                    var type = (fileLike && fileLike.type ? fileLike.type : '').toLowerCase();
                    if (fileName.length) { return /\.(jpe?g|png|gif|pdf)$/.test(fileName); }
                    return type.indexOf('image/') === 0 || type.indexOf('pdf') >= 0;
                }
                function pastedFile(event) {
                    var data = event.clipboardData;
                    if (!data) { return null; }
                    var files = data.files || [];
                    for (var i = 0; i < files.length; i++) {
                        if (attachmentish(files[i])) { return files[i]; }
                    }
                    var items = data.items || [];
                    for (var j = 0; j < items.length; j++) {
                        if (items[j].kind !== 'file') { continue; }
                        var file = items[j].getAsFile ? items[j].getAsFile() : null;
                        if (attachmentish(file)) { return file; }
                    }
                    return null;
                }
                function submitIt() {
                    /* requestSubmit, not submit: the submit event has to run, or the Enter key is
                       never taken back and the same file goes up twice */
                    if (form.requestSubmit) { form.requestSubmit(button); } else { form.submit(); }
                }
                function take(file) {
                    try {
                        var transfer = new DataTransfer();
                        transfer.items.add(file);
                        fileField.files = transfer.files;
                    } catch (e) {
                        /* old browser: nothing is claimed below, because the input stays empty */
                    }
                    var held = fileField.files && fileField.files.length > 0;
                    say(held
                        ? '已粘贴：' + file.name + '，按回车上传（或点「上传」）'
                        : '这个浏览器不让把文件放进输入框，请点「选择文件」', held ? 'ok' : 'warn');
                    if (!held) { return; }
                    if (window.mfgAskEnter) { window.mfgAskEnter(name, button, submitIt); }
                    if (window.jumpUnderNav) { window.jumpUnderNav(hint); }
                    try { button.focus({ preventScroll: true }); } catch (e2) { button.focus(); }
                }
                document.addEventListener('paste', function (event) {
                    /* a paste that carries no file is someone typing: left alone */
                    var file = pastedFile(event);
                    if (!file) { return; }
                    event.preventDefault();
                    take(file);
                });
                form.addEventListener('submit', function () {
                    if (window.mfgEnterDone) { window.mfgEnterDone(name); }
                });
            }());
        </script>
    </fieldset>
    <#if attachments?has_content>
        <table>
            <tr><th>文件</th><th class="num">序号</th><th>文件名</th><th class="num">大小</th><th>备注</th><th>上传时间</th><th>操作</th></tr>
            <#list attachments as att>
                <tr>
                    <td>
                        <#if att.isImage>
                            <a href="/mfg/control/attachment?ownerTypeId=${ownerTypeId}&ownerId=${ownerId}&seq=${att.seq}" target="_blank">
                                <img src="/mfg/control/attachment?ownerTypeId=${ownerTypeId}&ownerId=${ownerId}&seq=${att.seq}&thumb=1" alt="${att.fileName!}" height="80" style="border: 1px solid #3c3c3c;"/>
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
                            <input type="hidden" name="back" value="${back}#attachments"/>
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
