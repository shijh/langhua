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
            <#if parameters.err == 'size'>文件太大：图片 / SVG ≤ 4 MB，PDF ≤ 8 MB
            <#elseif parameters.err == 'type'>只支持 JPG / PNG / GIF / SVG / PDF
            <#elseif parameters.err == 'nofile'>请先选择文件
            <#elseif parameters.err == 'owner'>这条记录还不存在，先保存记录再上传
            <#elseif parameters.err == 'note'>备注最长 255 个字，删掉一些再存
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
                <input type="file" name="file" id="${fileId}" accept="image/jpeg,image/png,image/gif,image/svg+xml,application/pdf"/>
                <input type="text" name="note" size="22" placeholder="备注：正面 / 破损 / 合同扫描件"/>
                <input type="submit" value="上传" id="${buttonId}"/>
            </p>
            <p class="hint" id="${hintId}">支持 JPG / PNG / GIF / SVG / PDF；图片与 SVG ≤ 4 MB，PDF ≤ 8 MB。
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
                /* what this block takes: exactly the types the upload endpoint stores */
                function attachmentish(fileLike) {
                    var fileName = (fileLike && fileLike.name ? fileLike.name : '').toLowerCase();
                    var type = (fileLike && fileLike.type ? fileLike.type : '').toLowerCase();
                    if (fileName.length) { return /\.(jpe?g|png|gif|svg|pdf)$/.test(fileName); }
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
                        </#if>                    </td>
                    <td class="num">${att.seq}</td>
                    <td>${att.fileName!}</td>
                    <td class="num">${((att.fileSize!0) / 1024)?string("0.#")} KB</td>
                    <td>
                        <#-- 备注先按原文完整显示（换行保留）：输入框只有一行，长备注会被截断，看不全就不舒服。
                             要改再展开——改的是同一个字段，文件本身不动。 -->
                        <div class="note-text" data-att-note-text="${att.seq}"><#if (att.note!'')?has_content>${att.note}<#else><span class="hint">—</span></#if></div>
                        <details class="att-note-edit">
                            <summary class="hint">改备注</summary>
                            <form method="post" action="/mfg/control/attachmentNote" class="inline">
                                <input type="hidden" name="ownerTypeId" value="${ownerTypeId}"/>
                                <input type="hidden" name="ownerId" value="${ownerId}"/>
                                <input type="hidden" name="seq" value="${att.seq}"/>
                                <input type="hidden" name="back" value="${back}#attachments"/>
                                <textarea name="note" rows="3" cols="32" placeholder="备注（最多 ${att.noteLimit} 字）" data-att-note="${att.seq}">${att.note!}</textarea>
                                <input type="submit" value="存备注"/>
                                <span class="hint" data-att-note-counter="${att.noteLimit}">已输入 ${(att.note!'')?length} / ${att.noteLimit} 字</span>
                            </form>
                        </details>
                    </td>
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
        <script>
            /* 备注字数：上限从服务来（data-att-note-counter 的属性），超了当场拦下——服务端拒绝会回到
               本页、刚打的字就没了，所以宁可在这里拦住（服务和页面用的是同一个常量）。 */
            (function () {
                function wire(box) {
                    var counter = box.parentNode ? box.parentNode.querySelector('[data-att-note-counter]') : null;
                    if (!counter) { return; }
                    var limit = parseInt(counter.getAttribute('data-att-note-counter'), 10) || 0;
                    function show() {
                        var used = box.value.length;
                        counter.textContent = '已输入 ' + used + ' / ' + limit + ' 字';
                        counter.className = used > limit ? 'error' : 'hint';
                        counter.title = used > limit ? ('超出 ' + (used - limit) + ' 个字，存不进去') : '';
                    }
                    box.addEventListener('input', show);
                    show();
                    var form = box.form;
                    if (!form) { return; }
                    form.addEventListener('submit', function (event) {
                        if (box.value.length <= limit) { return; }
                        event.preventDefault();
                        event.stopImmediatePropagation();
                        window.alert('备注最长 ' + limit + ' 个字（现在 ' + box.value.length + ' 个），删掉一些再存');
                        box.focus();
                    });
                }
                var boxes = document.querySelectorAll('textarea[data-att-note]');
                for (var i = 0; i < boxes.length; i++) {
                    wire(boxes[i]);
                }
            }());
        </script>
    <#else>
        <p class="hint">还没有附件。</p>
    </#if>
</#macro>
