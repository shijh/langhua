<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  User accounts: who may log in, which security group they belong to, whether they still use the
  default password. Only members of the SUPER group get the editing forms.
  Data: accountList (mfgUserList -> rows / groupOptions / canManage / message)
-->
<#assign al = (accountList)!{}>
<#assign rows = (al.rows)![]>
<#assign groupOptions = (al.groupOptions)![]>
<#assign canManage = ((al.canManage)!'N') == 'Y'>
<#assign currentUser = (al.currentUserLoginId)!''>
<#assign errList = (errorMessageList)![]>
<#assign okList = (infoMessageList)![]>

<h1>用户账号</h1>

<#if errList?has_content>
    <p class="error"><#list errList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>
<#if okList?has_content>
    <p class="ok"><#list okList as msg>${msg}<#if msg_has_next><br/></#if></#list></p>
</#if>

<#if !canManage>
    <p class="warn">${(al.message)!'只有管理员可以管理账号'}</p>
</#if>

<#if canManage>
    <fieldset>
        <legend>新建 / 修改账号</legend>
        <form method="post" action="/mfg/control/userSave">
            <p>
                账号 <input type="text" name="userLoginId" size="16" maxlength="20" required="required"
                          placeholder="字母数字，最多 20 位"/>
                姓名 <input type="text" name="personName" size="16" placeholder="显示在账号列表里"/>
            </p>
            <p>
                安全组
                <select name="groupId" class="compact">
                    <#list groupOptions as g>
                        <option value="${g.groupId}"<#if g.groupId == 'SUPER'> selected="selected"</#if>>${g.groupId}</option>
                    </#list>
                </select>
                状态
                <select name="enabled" class="compact">
                    <option value="Y">启用</option>
                    <option value="N">停用</option>
                </select>
            </p>
            <p>
                初始密码 <input type="password" name="password" size="16" autocomplete="new-password"
                              placeholder="留空为 ofbiz（首次登录强制修改）"/>
                密码提示 <input type="text" name="passwordHint" size="20" placeholder="可选"/>
                <input type="submit" value="保存"/>
            </p>
            <p class="hint">账号已存在时只更新姓名、安全组与状态，密码不会被改；改密码用下面每行的「重置密码」。
                一个账号只属于一个安全组；下拉里只列能进本系统的组，默认 <code>SUPER</code>。</p>
        </form>
    </fieldset>
</#if>

<#if canManage>
<h2>账号清单</h2>
<#if rows?has_content>
    <table id="accountTable">
        <tr><th>账号</th><th>姓名</th><th>安全组</th><th>状态</th><th>密码</th><th>建立日期</th><th>操作</th></tr>
        <#list rows as r>
            <tr data-account="${r.userLoginId!}" data-enabled="${r.enabled!}" data-groups="${r.groups!}"
                data-default-password="${r.defaultPassword!}" data-self="${r.self!}">
                <td>${r.userLoginId!}<#if ((r.self)!'N') == 'Y'> <span class="hint">（当前登录）</span></#if></td>
                <td>${r.personName!}</td>
                <td><#if ((r.groups)!'')?has_content>${r.groups}<#else>—</#if></td>
                <td class="nowrap"><#if ((r.enabled)!'Y') == 'Y'>启用<#else><span class="warn">已停用</span></#if></td>
                <td class="nowrap"><#if ((r.defaultPassword)!'N') == 'Y'><span class="warn">出厂密码</span><#else>已自设</#if></td>
                <td class="nowrap">${(r.createdDate)!''}</td>
                <td class="nowrap">
                    <form method="post" action="/mfg/control/userResetPassword" class="inline">
                        <input type="hidden" name="userLoginId" value="${r.userLoginId!}"/>
                        <button type="submit" class="secondary">重置密码</button>
                    </form>
                    <form method="post" action="/mfg/control/userSave" class="inline">
                        <input type="hidden" name="userLoginId" value="${r.userLoginId!}"/>
                        <#if ((r.enabled)!'Y') == 'Y'>
                            <input type="hidden" name="enabled" value="N"/>
                            <button type="submit" class="secondary">停用</button>
                        <#else>
                            <input type="hidden" name="enabled" value="Y"/>
                            <button type="submit" class="secondary">启用</button>
                        </#if>
                    </form>
                </td>
            </tr>
        </#list>
    </table>
<#else>
    <p class="hint">还没有账号记录。</p>
</#if>

<p class="hint">共 ${(al.totalCount)!0} 个账号。新账号第一次登录会被要求改密码（出厂密码不能长期使用）；
    忘记密码就让管理员在这里点「重置密码」，重置后同样要求改。</p>
</#if>
