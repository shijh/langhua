<#ftl encoding="UTF-8">
<#setting url_escaping_charset="UTF-8">
<#--
  User accounts: who may log in, which security group they belong to, whether they still use the
  default password. Only members of the SUPER group get the editing forms.
  Data: accountList (mfgUserList -> rows / groupOptions / canManage / message),
        auditLog (mfgAuditList -> rows / options / page / pageCount / totalCount / keepingDays)
-->
<#import "pager.ftl" as pg>
<#assign al = (accountList)!{}>
<#assign rows = (al.rows)![]>
<#assign groupOptions = (al.groupOptions)![]>
<#assign canManage = ((al.canManage)!'N') == 'Y'>
<#assign currentUser = (al.currentUserLoginId)!''>
<#assign audit = (auditLog)!{}>
<#assign auditRows = (audit.rows)![]>
<#assign auditOptions = (audit.options)![]>
<#assign auditCanManage = ((audit.canManage)!'N') == 'Y'>
<#assign auditPairs = []>
<#if ((audit.search)!'')?has_content><#assign auditPairs = auditPairs + ['search=' + audit.search?url]></#if>
<#if ((audit.actionId)!'')?has_content><#assign auditPairs = auditPairs + ['actionId=' + audit.actionId?url]></#if>
<#assign auditQuery = auditPairs?join('&')>
<#assign auditPage = (audit.page)!1>
<#assign auditPages = (audit.pageCount)!1>
<#assign auditTotal = (audit.totalCount)!0>
<#assign auditSize = (audit.pageSize)!20>
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

<h2>操作记录</h2>
<#if ((parameters.purged)!'')?has_content>
    <p class="ok">已删除 ${parameters.purged} 条一年以前的记录。</p>
</#if>
<#if !auditCanManage>
    <p class="warn">${(audit.message)!'只有管理员可以查看操作记录'}</p>
<#else>
<#-- who logged in, who was refused, who asked for a password hint, who changed a password -->
<form method="get" action="/mfg/control/users">
    <p>
        账号 / 目标 <input type="text" name="search" value="${(audit.search)!}" size="16" placeholder="账号或对方账号"/>
        动作
        <select name="actionId" class="compact">
            <option value="">全部</option>
            <#list auditOptions as o>
                <option value="${o.actionId}"<#if o.actionId == ((audit.actionId)!'')> selected="selected"</#if>>${o.label}</option>
            </#list>
        </select>
        <input type="submit" value="筛选"/>
    </p>
</form>
<#if auditRows?has_content>
    <table id="auditTable">
        <tr><th>时间</th><th>动作</th><th>谁</th><th>对象</th><th>来源</th><th>说明</th></tr>
        <#list auditRows as a>
            <tr data-audit="${a.auditId!}" data-action="${a.actionId!}">
                <td class="nowrap">${a.when!}</td>
                <td class="nowrap">${a.actionLabel!}</td>
                <td><#if (a.userLoginId!)?has_content>${a.userLoginId}<#else>—</#if></td>
                <td><#if (a.targetId!)?has_content>${a.targetId}<#else>—</#if></td>
                <td class="nowrap"><#if (a.remoteAddr!)?has_content>${a.remoteAddr}<#else>—</#if></td>
                <td class="hint">${a.note!}</td>
            </tr>
        </#list>
    </table>
    <@pg.pager action="/mfg/control/users" query=auditQuery page=auditPage pageCount=auditPages total=auditTotal unit="条" pageSize=auditSize/>
<#else>
    <p class="hint">还没有记录。登录、登录失败、查看密码提示、修改密码都会记一行。</p>
</#if>
<p class="hint">只保留 ${(audit.keepingDays)!365} 天，过期的记录每天自动清一次（也可以在下面手动清）。
    <#if ((audit.lastPurge)!'')?has_content>上次清理：${audit.lastPurge}。</#if></p>
<form method="post" action="/mfg/control/auditPurge" class="inline"
      onsubmit="return confirm('删除一年以前的记录？这个动作不会影响账号和单据。');">
    <#-- the audit list is the last block of the page: come back to it (UI rule: 改完停在原地) -->
    <input type="hidden" name="backTo" value="auditTable"/>
    <button type="submit" class="secondary">清理一年以前的记录</button>
</form>
</#if>
